package dev.hubops

import dev.hubops.core.integration.EngineId
import dev.hubops.core.integration.HubOpsOrchestrator
import dev.hubops.core.integration.ValidationEvidence
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class HubOpsOrchestratorTest {
    private fun zip(file: File, entries: Map<String, String>) {
        ZipOutputStream(file.outputStream()).use { out ->
            entries.forEach { (name, body) ->
                out.putNextEntry(ZipEntry(name)); out.write(body.toByteArray()); out.closeEntry()
            }
        }
    }

    private fun evidence(accepted: Boolean) = ValidationEvidence(
        filesChecked = 1, expectedImportedFiles = 1, errors = if (accepted) 0 else 1, warnings = 0,
        astExpectedFiles = 1, astParsedFiles = 1, astUnavailableFiles = 0,
        astSyntaxErrorFiles = 0, astTruncatedFiles = 0, filesOmitted = 0,
        evidenceSha256 = "evidence", limitations = emptyList(),
    )

    @Test fun transferIsOnlyPromotedAfterValidation() {
        val root = Files.createTempDirectory("hubops-orchestrator").toFile()
        val archive = File(root, "project.zip")
        val workspace = File(root, "workspace").apply { mkdirs() }
        zip(archive, mapOf("app/src/Main.kt" to "class Main"))
        val coordinator = HubOpsOrchestrator(File(root, "ledger"))
        val proposal = coordinator.prepareZipTransfer(archive, EngineId.DESPERTAR, EngineId.ESPELHAR_X,
            "projects/despertar/test", workspace)
        assertEquals(1, proposal.impact.supportedFiles)
        val outcome = coordinator.executeTransfer(proposal.id, archive, "staging/${proposal.id}",
            importToStaging = { _, stage -> File(workspace, stage).mkdirs(); File(workspace, "$stage/Main.kt").writeText("class Main"); 1 },
            validateStaging = { evidence(false) },
            promoteStaging = { _, _ -> throw AssertionError("Não pode promover sem validação") })
        assertFalse(outcome.accepted)
        assertEquals(0, outcome.promotedFiles)
        assertTrue(File(workspace, "staging/${proposal.id}/Main.kt").isFile)
        assertFalse(File(workspace, "projects/despertar/test/Main.kt").exists())
        assertTrue(coordinator.recentAuditLines().any { it.contains("VALIDATION_BLOCKED") })
    }

    @Test fun changedArtifactCannotBeCommitted() {
        val root = Files.createTempDirectory("hubops-orchestrator-hash").toFile()
        val archive = File(root, "project.zip")
        val workspace = File(root, "workspace").apply { mkdirs() }
        zip(archive, mapOf("Main.kt" to "class Main"))
        val coordinator = HubOpsOrchestrator(File(root, "ledger"))
        val proposal = coordinator.prepareZipTransfer(archive, EngineId.DESPERTAR, EngineId.ESPELHAR_X, "projects/test", workspace)
        val privateArtifact = requireNotNull(coordinator.artifactFile(proposal.id))
        zip(privateArtifact, mapOf("Main.kt" to "class Tampered"))
        val failure = runCatching {
            coordinator.executeTransfer(proposal.id, privateArtifact, "staging/${proposal.id}", { _, _ -> 0 }, { evidence(true) }, { _, _ -> 0 })
        }.exceptionOrNull()
        assertNotNull(failure)
        assertTrue(failure!!.message!!.contains("mudou"))
    }

    @Test fun pendingProposalAndPrivateArtifactSurviveCoordinatorRestart() {
        val root = Files.createTempDirectory("hubops-orchestrator-persist").toFile()
        val archive = File(root, "project.zip")
        val workspace = File(root, "workspace").apply { mkdirs() }
        zip(archive, mapOf("README.md" to "# project"))
        val first = HubOpsOrchestrator(File(root, "ledger"))
        val proposal = first.prepareZipTransfer(archive, EngineId.DESPERTAR, EngineId.ESPELHAR_X, "projects/persist", workspace)
        val second = HubOpsOrchestrator(File(root, "ledger"))
        val restored = second.pendingProposals().single()
        assertEquals(proposal.id, restored.id)
        assertTrue(second.artifactFile(proposal.id)?.isFile == true)
    }

    @Test fun interruptedTransferReconcilesJournalProposalArtifactAndStagingReadOnly() {
        val root = Files.createTempDirectory("hubops-reconcile").toFile()
        val archive = File(root, "project.zip")
        val workspace = File(root, "workspace").apply { mkdirs() }
        val state = File(root, "ledger")
        zip(archive, mapOf("Main.kt" to "class Main"))
        val coordinator = HubOpsOrchestrator(state)
        val proposal = coordinator.prepareZipTransfer(archive, EngineId.DESPERTAR, EngineId.ESPELHAR_X,
            "projects/reconcile", workspace)
        val stage = File(workspace, "integration-staging/${proposal.id}").apply { mkdirs() }
        File(stage, "Main.kt").writeText("class Main")
        coordinator.executeTransfer(proposal.id, requireNotNull(coordinator.artifactFile(proposal.id)),
            "integration-staging/${proposal.id}",
            importToStaging = { _, _ -> 1 },
            validateStaging = { evidence(false) },
            promoteStaging = { _, _ -> error("A validação bloqueada não pode promover") })
        val before = File(stage, "Main.kt").readText()
        val reconciled = HubOpsOrchestrator(state).reconcileInterruptedTransactions(workspace)
        assertEquals(1, reconciled.size)
        assertEquals(dev.hubops.core.integration.ReconciliationStatus.STAGING_AVAILABLE_FOR_REVIEW, reconciled.single().status)
        assertTrue(reconciled.single().artifactAvailable)
        assertTrue(reconciled.single().stagingAvailable)
        assertEquals(before, File(stage, "Main.kt").readText())
    }

}
