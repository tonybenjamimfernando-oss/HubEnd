package dev.hubops.core.integration

import dev.hubops.core.code.AstProjectReport
import dev.hubops.despertar.project.ProjectInsight
import dev.hubops.despertar.project.ProjectSymbolIndex
import com.hubops.vislumbrops.core.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CrossEngineAssessmentCoordinatorTest {
    private fun insight(ast: AstProjectReport) = ProjectInsight(
        filesScanned = 2, sourceFiles = 1, testFiles = 0, kotlinFiles = 1, javaFiles = 0,
        pythonFiles = 0, todoCount = 0, findings = emptyList(),
        symbolIndex = ProjectSymbolIndex(1, 0, 0, 0, emptyList(), 0), astReport = ast
    )

    private fun contract(status: VerificationStatus = VerificationStatus.PASSED) = VerificationContract(
        projectId = "sample", artifactPath = "/private/build/sample.apk", artifactHash = "a".repeat(64),
        verificationId = "vis-1", status = status,
        checks = listOf(VerificationCheck("APK-HASH", CheckCategory.ARTIFACT_INTEGRITY, "hash", CheckResult.PASS)),
        findings = listOf(Finding(category = CheckCategory.SECRETS, severity = Severity.HIGH,
            title = "Possível segredo", description = "Achado do scanner VislumbOps"))
    )

    @Test fun joinsAstAndVislumbFindingsWithoutConfusingArtifactHashes() {
        val ast = AstProjectReport(1, 0, 1, 0, 1, 0, 12, emptyList(),
            listOf(dev.hubops.core.code.AstFileResult("src/Main.kt", "Kotlin", 12, 1, true, false, emptyList())), emptyList())
        val zip = File.createTempFile("source", ".zip").apply { writeText("fixture-source-archive") }
        try {
            val result = CrossEngineAssessmentCoordinator { insight(ast) }.assess(zip, "build:42", contract())
            assertEquals("build:42", result.buildReference)
            assertEquals(64, result.sourceArchiveSha256.length)
            assertEquals("a".repeat(64), result.binaryArtifactSha256)
            assertNotEquals(result.sourceArchiveSha256, result.binaryArtifactSha256)
            assertEquals(1, result.astFilesWithSyntaxErrors)
            assertTrue(result.findings.any { it.title == "Possível segredo" })
            assertTrue(result.findings.any { it.title == "Erros sintáticos detetados pela AST" })
            assertTrue(result.requiresReview)
            assertEquals(64, result.evidenceSha256.length)
            assertTrue(result.limitations.any { it.contains("não prova") })
        } finally { zip.delete() }
    }

    @Test fun refusesIncompleteVislumbContract() {
        val zip = File.createTempFile("source", ".zip").apply { writeText("fixture") }
        try {
            val error = runCatching {
                CrossEngineAssessmentCoordinator { insight(AstProjectReport(0,0,0,0,0,0,0, emptyList(), emptyList(), emptyList())) }
                    .assess(zip, "build-1", contract(VerificationStatus.RUNNING))
            }.exceptionOrNull()
            assertNotNull(error)
            assertTrue(error!!.message!!.contains("ainda não terminou"))
        } finally { zip.delete() }
    }

    @Test fun refusesInvalidBuildReferenceAndInvalidBinaryHash() {
        val zip = File.createTempFile("source", ".zip").apply { writeText("fixture") }
        val ast = AstProjectReport(0,0,0,0,0,0,0, emptyList(), emptyList(), emptyList())
        val coordinator = CrossEngineAssessmentCoordinator { insight(ast) }
        try {
            assertNotNull(runCatching { coordinator.assess(zip, "../build", contract()) }.exceptionOrNull())
            assertNotNull(runCatching { coordinator.assess(zip, "build-1", contract().copy(artifactHash = "bad")) }.exceptionOrNull())
        } finally { zip.delete() }
    }
    @Test fun incompleteAstCoverageAndUnverifiedProvenanceRequireReview() {
        val ast = AstProjectReport(2, 0, 1, 1, 0, 0, 10, emptyList(),
            listOf(dev.hubops.core.code.AstFileResult("src/Main.kt", "Kotlin", 10, 0, false, false, emptyList())),
            listOf("Java parser indisponível"))
        val zip = File.createTempFile("source-incomplete", ".zip").apply { writeText("fixture-source") }
        try {
            val cleanContract = contract().copy(findings = emptyList())
            val result = CrossEngineAssessmentCoordinator { insight(ast) }.assess(zip, "build-incomplete", cleanContract)
            assertTrue(result.requiresReview)
            assertEquals(CheckResult.WARN, result.checks.first { it.id == "ESPELHAR-AST-COVERAGE" }.result)
            assertEquals(CheckResult.WARN, result.checks.first { it.id == "CROSS-ENGINE-PROVENANCE" }.result)
            assertTrue(result.limitations.any { it.contains("atestação assinada") })
        } finally { zip.delete() }
    }

}
