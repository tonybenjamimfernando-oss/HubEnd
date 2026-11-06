package dev.hubops.espelhar

import dev.hubops.despertar.project.ComponentFusion
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class EspelharProjectServicesTest {
    @Test fun dependencyGraphAssociatesLocalDeclarations() {
        val graph = EspelharProjectServices.dependencyGraph(mapOf(
            "src/main/Foo.kt" to "package sample\nclass Foo",
            "src/main/Use.kt" to "package sample\nimport sample.Foo\nclass Use",
        ))
        assertEquals(1, graph.resolved)
        assertEquals(0, graph.unresolved)
        assertEquals("associated", graph.edges.single().status)
    }

    @Test fun repairRequiresUnchangedHashAndCreatesBackup() {
        val root = Files.createTempDirectory("espelhar-repair").toFile()
        try {
            val source = java.io.File(root, "Main.kt").apply { writeText("fun main() {}  ") }
            val proposal = EspelharProjectServices.proposeRepairs(mapOf("Main.kt" to source.readText()))
                .candidates.first { it.id == "normalize-whitespace" }
            val backup = EspelharProjectServices.applyRepair(root, proposal)
            assertEquals("fun main() {}  ", backup.readText())
            assertEquals("fun main() {}", source.readText())
            assertTrue(backup.isFile)
            assertThrows(IllegalArgumentException::class.java) {
                EspelharProjectServices.applyRepair(root, proposal)
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun repairCanBeRevertedOnlyWhenAppliedContentIsUnchanged() {
        val root = Files.createTempDirectory("espelhar-rollback").toFile()
        try {
            val source = java.io.File(root, "Main.kt").apply { writeText("fun main() {}  ") }
            val proposal = EspelharProjectServices.proposeRepairs(mapOf("Main.kt" to source.readText()))
                .candidates.first { it.id == "normalize-whitespace" }
            EspelharProjectServices.applyRepair(root, proposal)
            val archived = EspelharProjectServices.rollbackRepair(root, proposal)
            assertEquals("fun main() {}  ", source.readText())
            assertTrue(archived.isFile)
            assertTrue(archived.name.contains("hubops-backup"))
        } finally { root.deleteRecursively() }
    }

    @Test fun rollbackRejectsTamperedBackup() {
        val root = Files.createTempDirectory("espelhar-rollback-backup").toFile()
        try {
            val source = java.io.File(root, "Main.kt").apply { writeText("fun main() {}  ") }
            val proposal = EspelharProjectServices.proposeRepairs(mapOf("Main.kt" to source.readText()))
                .candidates.first { it.id == "normalize-whitespace" }
            EspelharProjectServices.applyRepair(root, proposal)
            java.io.File(root, "Main.kt.hubops-backup").writeText("tampered")
            assertThrows(IllegalArgumentException::class.java) {
                EspelharProjectServices.rollbackRepair(root, proposal)
            }
            assertEquals("fun main() {}", source.readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun rollbackRefusesToOverwriteChangesMadeAfterRepair() {
        val root = Files.createTempDirectory("espelhar-rollback-conflict").toFile()
        try {
            val source = java.io.File(root, "Main.kt").apply { writeText("fun main() {}  ") }
            val proposal = EspelharProjectServices.proposeRepairs(mapOf("Main.kt" to source.readText()))
                .candidates.first { it.id == "normalize-whitespace" }
            EspelharProjectServices.applyRepair(root, proposal)
            source.writeText("user changed this")
            assertThrows(IllegalArgumentException::class.java) {
                EspelharProjectServices.rollbackRepair(root, proposal)
            }
            assertEquals("user changed this", source.readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun repairRejectsTraversal() {
        val root = Files.createTempDirectory("espelhar-path").toFile()
        try {
            val proposal = EspelharRepair("final-newline", "../outside.kt", "x", "x", "x", "x\n")
            assertThrows(IllegalArgumentException::class.java) {
                EspelharProjectServices.applyRepair(root, proposal)
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun androidBuildPreflightFindsMissingWrapperJar() {
        val readiness = EspelharProjectServices.buildReadiness(mapOf(
            "app/src/main/AndroidManifest.xml" to "",
            "build.gradle.kts" to "",
            "settings.gradle.kts" to "",
            "app/build.gradle.kts" to "",
            "gradlew" to "",
            "gradle/wrapper/gradle-wrapper.properties" to "",
            "app/src/main/MainActivity.kt" to "",
        ))
        assertEquals("review", readiness.status)
        assertTrue(readiness.wrapperScript)
        assertFalse(readiness.wrapperJar)
        assertTrue(readiness.warnings.any { it.contains("Wrapper incompleto") })
    }

    @Test fun snapshotImportDoesNotOverwriteExistingFiles() {
        val root = Files.createTempDirectory("espelhar-import").toFile()
        try {
            val engine = EspelharEngine(root)
            assertEquals(1, engine.importSources(mapOf("src/Main.kt" to "class Main"), "github/demo"))
            val target = java.io.File(root, "github/demo/src/Main.kt")
            assertEquals("class Main", target.readText())
            assertThrows(IllegalArgumentException::class.java) {
                engine.importSources(mapOf("src/Main.kt" to "class Changed"), "github/demo")
            }
            assertEquals("class Main", target.readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun componentFusionReportsExactPathConflictWithoutMutation() {
        val primary = mapOf("src/Main.kt" to "package a\nclass Main")
        val reference = mapOf("src/Main.kt" to "package b\nclass Main")
        val report = ComponentFusion.analyze(primary, reference)
        assertEquals(listOf("src/Main.kt"), report.exactPathConflicts)
        assertEquals("package a\nclass Main", primary.getValue("src/Main.kt"))
    }
    @Test fun dependencyGraphDoesNotTreatEveryFileInPackageAsImportedClass() {
        val graph = EspelharProjectServices.dependencyGraph(mapOf(
            "src/Foo.kt" to "package sample\nclass Foo",
            "src/Bar.kt" to "package sample\nclass Bar",
            "src/Use.kt" to "package consumer\nimport sample.Foo\nclass Use",
        ))
        assertEquals(1, graph.resolved)
        assertEquals("src/Foo.kt", graph.edges.single().candidates.single())
    }

    @Test fun snapshotDiffIsReadOnlyAndClassifiesChanges() {
        val root = Files.createTempDirectory("espelhar-diff").toFile()
        try {
            val folder = java.io.File(root, "github/acme/demo").apply { mkdirs() }
            java.io.File(folder, "same.kt").writeText("class Same")
            java.io.File(folder, "changed.kt").writeText("class Old")
            java.io.File(folder, "local.kt").writeText("class Local")
            val diff = EspelharProjectServices.compareSnapshotToWorkspace(root,
                mapOf("same.kt" to "class Same", "changed.kt" to "class New", "added.kt" to "class Added"),
                "github/acme/demo",
            )
            assertEquals(listOf("added.kt"), diff.added)
            assertEquals(listOf("changed.kt"), diff.changed)
            assertEquals(listOf("same.kt"), diff.unchanged)
            assertEquals(listOf("local.kt"), diff.localOnly)
            assertEquals("class Old", java.io.File(folder, "changed.kt").readText())
        } finally { root.deleteRecursively() }
    }

}
