package dev.hubops

import dev.hubops.despertar.project.ProjectIntelligence
import dev.hubops.despertar.project.ProjectArchitect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectIntelligenceTest {
    @Test fun countsLanguagesAndTestFiles() {
        val result = ProjectIntelligence.analyze(mapOf(
            "app/src/main/java/Main.kt" to "package sample\nclass Main",
            "app/src/main/java/Legacy.java" to "package sample; class Legacy {}",
            "app/src/test/java/MainTest.kt" to "package sample\nclass MainTest",
            "settings.gradle.kts" to "include(\":app\")",
            "build.gradle.kts" to "plugins {}",
            "app/src/main/AndroidManifest.xml" to "<manifest />",
            "gradlew" to "#!/bin/sh",
        ))
        assertEquals(2, result.sourceFiles)
        assertEquals(1, result.testFiles)
        assertEquals(1, result.kotlinFiles)
        assertEquals(1, result.javaFiles)
    }

    @Test fun architectMapsModulesPackagesAndDependencies() {
        val report = ProjectArchitect.analyze(mapOf(
            "settings.gradle.kts" to "include(\":app\", \":core\")",
            "app/src/main/java/dev/sample/ui/Home.kt" to "package dev.sample.ui\nimport dev.sample.data.Repository\nclass Home",
            "app/src/main/java/dev/sample/data/Repository.kt" to "package dev.sample.data\nimport dev.sample.domain.Model\nclass Repository",
            "app/src/main/java/dev/sample/domain/Model.kt" to "package dev.sample.domain\nclass Model",
        ))
        assertEquals(3, report.packageCount)
        assertEquals(3, report.sourceCount)
        assertEquals(2, report.moduleNames.size)
        assertTrue(report.packageDependencies.any { it.contains("dev.sample.ui → dev.sample.data") })
        assertTrue(report.implementationPlan.isNotEmpty())
    }

    @Test fun architectFlagsLikelyTwoWayPackageDependency() {
        val report = ProjectArchitect.analyze(mapOf(
            "A.kt" to "package sample.a\nimport sample.b.B",
            "B.kt" to "package sample.b\nimport sample.a.A",
        ))
        assertTrue(report.findings.any { it.title == "Dependência circular provável" })
    }

    @Test fun reportsPendingMarkersAndMissingWrapper() {
        val result = ProjectIntelligence.analyze(mapOf(
            "Main.kt" to "package sample\n// TODO finish\n// FIXME later",
            "settings.gradle.kts" to "include(\":app\")",
            "build.gradle.kts" to "plugins {}",
            "AndroidManifest.xml" to "<manifest />",
        ))
        assertEquals(2, result.todoCount)
        assertTrue(result.findings.any { it.title == "Trabalho pendente" })
        assertTrue(result.findings.any { it.title == "Gradle Wrapper ausente ou incompleto" })
        assertTrue(result.findings.any { it.title == "Testes não identificados" })
    }

    @Test fun detectsMissingAndroidStructure() {
        val result = ProjectIntelligence.analyze(mapOf("README.md" to "sample"))
        assertTrue(result.findings.any { it.title == "Código-fonte não localizado" })
        assertTrue(result.findings.any { it.title == "Manifesto Android ausente" })
        assertTrue(result.findings.any { it.title == "Configuração Gradle incompleta" })
    }

    @Test fun reportsLargeFilesWithoutChangingContent() {
        val content = "package sample\n" + (1..510).joinToString("\n") { "// line $it" }
        val result = ProjectIntelligence.analyze(mapOf("Large.kt" to content))
        assertTrue(result.findings.any { it.title == "Ficheiro extenso" })
    }
    @Test fun symbolIndexFindsDeclarationsAndCrossFileOccurrencesButIgnoresCommentsAndStrings() {
        val result = ProjectIntelligence.analyze(mapOf(
            "src/main/Repository.kt" to """package sample
                class Repository
                fun loadRepository(): Repository = Repository()
                // Repository should not be counted from this comment
                val note = "Repository in a string is not a reference"
            """.trimIndent(),
            "src/main/Screen.kt" to """package sample
                class Screen(private val repository: Repository) {
                    fun render() = repository.loadRepository()
                }
            """.trimIndent(),
        ))
        val symbol = result.symbolIndex.symbols.first { it.name == "Repository" }
        assertEquals(1, symbol.declarationPaths.size)
        assertTrue(symbol.referenceCount >= 2)
        assertTrue(symbol.referencePaths.contains("src/main/Screen.kt"))
        assertTrue(result.toMarkdown().contains("Índice lexical de símbolos"))
    }

    @Test fun symbolIndexFlagsDuplicateNamesInsteadOfClaimingSemanticResolution() {
        val result = ProjectIntelligence.analyze(mapOf(
            "a/One.kt" to "package a\nclass Shared",
            "b/Two.kt" to "package b\nclass Shared",
        ))
        val symbol = result.symbolIndex.symbols.first { it.name == "Shared" }
        assertTrue(symbol.ambiguous)
        assertEquals(2, symbol.declarationPaths.size)
        assertTrue(result.findings.any { it.title == "Nomes de símbolos ambíguos" })
    }

    @Test fun symbolIndexRecognizesJavaAndPythonDeclarations() {
        val result = ProjectIntelligence.analyze(mapOf(
            "src/Worker.java" to "package sample; public class Worker { public void execute() {} }",
            "scripts/worker.py" to "def execute_task():\n    return Worker()\n# Worker is only a comment here",
        ))
        assertTrue(result.symbolIndex.symbols.any { it.name == "Worker" && it.kinds.contains("tipo") })
        assertTrue(result.symbolIndex.symbols.any { it.name == "execute_task" && it.kinds.contains("função") })
    }

    @Test fun wrapperPropertiesAloneAreNotReportedAsAWorkingWrapper() {
        val result = ProjectIntelligence.analyze(mapOf(
            "settings.gradle.kts" to "include(\":app\")",
            "build.gradle.kts" to "plugins {}",
            "gradle/wrapper/gradle-wrapper.properties" to "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.11.1-bin.zip",
        ))
        assertTrue(result.findings.any { it.title == "Gradle Wrapper ausente ou incompleto" })
    }

    @Test fun astReportNeverClaimsUnparsedFilesWereAnalyzed() {
        val result = ProjectIntelligence.analyze(mapOf(
            "src/Main.kt" to "package sample\nclass Main",
            "src/Worker.java" to "class Worker {}",
            "scripts/task.py" to "def task():\n    return 1",
        ))
        assertEquals(3, result.astReport.filesConsidered)
        assertEquals(3, result.astReport.filesParsed + result.astReport.filesUnavailable)
        assertTrue(result.toMarkdown().contains("AST real · Tree-sitter"))
        if (result.astReport.filesParsed > 0) assertTrue(result.astReport.totalNodes > 0)
    }

}
