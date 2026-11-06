package dev.hubops.espelhar

import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EspelharEngineTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun detectsMutablePythonDefaultAndSymbols() {
        val result = EspelharEngine(temp.root).analyze("main.py", "class A:\n    pass\n\ndef f(items=[]):\n    return items\n")
        assertTrue(result.issues.any { it.type == "mutable_default" })
        assertTrue(result.symbols.any { it.name == "A" && it.type == "class" })
        assertTrue(result.symbols.any { it.name == "f" && it.type == "function" })
    }

    @Test fun detectsMismatchedBrackets() {
        val result = EspelharEngine(temp.root).analyze("main.js", "const value = ([1, 2);\n")
        assertTrue(result.issues.any { it.type == "mismatched_bracket" })
    }

    @Test fun ignoresBracketsInsideStringsAndComments() {
        val result = EspelharEngine(temp.root).analyze("main.py", "value = '(['  # )]\n")
        assertFalse(result.issues.any { it.type.contains("bracket") })
    }

    @Test fun ignoresBracketsInsidePythonTripleQuotedStrings() {
        val result = EspelharEngine(temp.root).analyze("main.py", "doc = \"\"\"texto ([ { ) ] }\"\"\"\nprint(1)\n")
        assertFalse(result.issues.any { it.type.contains("bracket") })
    }

    @Test fun returnsCachedAnalysisForUnchangedBuffer() {
        val engine = EspelharEngine(temp.root)
        engine.analyze("main.kt", "fun main() {}")
        assertTrue(engine.analyze("main.kt", "fun main() {}").cached)
    }

    @Test fun rejectsWorkspaceTraversalWhenSaving() {
        val engine = EspelharEngine(temp.root)
        val result = runCatching { engine.save("../escape.py", "print(1)") }
        assertTrue(result.isFailure)
    }

    @Test fun savesAndReadsWorkspaceFile() {
        val engine = EspelharEngine(temp.root)
        engine.save("src/main.py", "print('ok')")
        assertEquals("print('ok')", engine.read("src/main.py"))
    }
    @Test fun rejectsTraversalDuringAnalysisToo() {
        val result = runCatching { EspelharEngine(temp.root).analyze("../outside.py", "print(1)") }
        assertTrue(result.isFailure)
    }

    @Test fun rejectsTrailingGarbageInJson() {
        val result = EspelharEngine(temp.root).analyze("data.json", "{\"ok\":true} trailing")
        assertTrue(result.issues.any { it.type == "invalid_json" })
    }

    @Test fun importsSupportedProjectSourcesWithoutOverwriting() {
        val zipFile = temp.newFile("project.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            zip.putNextEntry(ZipEntry("demo/app/src/Main.kt"))
            zip.write("fun main() {}".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("demo/.gitignore"))
            zip.write("build/".toByteArray())
            zip.closeEntry()
        }
        val engine = EspelharEngine(temp.newFolder("workspace"))
        assertEquals(1, engine.importZip(zipFile))
        assertEquals("fun main() {}", engine.read("projects/despertar/demo/app/src/Main.kt"))
        assertTrue(runCatching { engine.importZip(zipFile) }.isFailure)
    }

    @Test fun rejectsZipSlipBeforeWriting() {
        val zipFile = temp.newFile("unsafe.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            zip.putNextEntry(ZipEntry("../escape.py"))
            zip.write("print(1)".toByteArray())
            zip.closeEntry()
        }
        val workspace = temp.newFolder("workspace")
        assertTrue(runCatching { EspelharEngine(workspace).importZip(zipFile) }.isFailure)
        assertFalse(temp.root.resolve("escape.py").exists())
    }

    @Test fun pipelineIncludesRealAstReportForSupportedLanguages() {
        val workspace = temp.newFolder("ast-workspace")
        workspace.resolve("Main.kt").writeText("class Main { fun run() {} }")
        val report = EspelharEngine(workspace).pipeline()
        assertNotNull(report.ast)
        assertEquals(1, report.ast!!.filesConsidered)
        assertTrue(report.tasks.any { it.name == "AST · Tree-sitter" })
    }

    @Test fun saveReplacesExistingFileWithoutLeavingTemporaryFiles() {
        val workspace = temp.newFolder("atomic-save")
        val engine = EspelharEngine(workspace)
        engine.save("main.py", "print('old')")
        engine.save("main.py", "print('new')")
        assertEquals("print('new')", engine.read("main.py"))
        assertFalse(workspace.listFiles().orEmpty().any { it.name.endsWith(".tmp") })
    }

    @Test fun importsGitHubSnapshotWithoutOverwriting() {
        val workspace = temp.newFolder("github-workspace")
        val engine = EspelharEngine(workspace)
        assertEquals(1, engine.importSources(mapOf("src/Main.kt" to "class Main"), "github-owner-repo"))
        assertEquals("class Main", engine.read("github-owner-repo/src/Main.kt"))
        assertTrue(runCatching { engine.importSources(mapOf("src/Main.kt" to "class Changed"), "github-owner-repo") }.isFailure)
        assertEquals("class Main", engine.read("github-owner-repo/src/Main.kt"))
    }

}
