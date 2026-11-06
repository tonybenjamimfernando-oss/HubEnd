package dev.hubops

import dev.hubops.core.integration.EngineId
import dev.hubops.core.integration.HubOpsOrchestrator
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class HubOpsExternalVerificationLedgerTest {
    @Test fun recordsVislumbOpsOutcomeWithoutProjectMutation() {
        val directory = Files.createTempDirectory("hubops-ledger-test").toFile()
        try {
            val ledger = HubOpsOrchestrator(directory)
            ledger.recordExternalVerification(
                sourceEngine = EngineId.VISLUMBOPS,
                destinationEngine = EngineId.HUBOPS,
                verificationId = "verification-test-001",
                artifactSha256 = "a".repeat(64),
                reportSha256 = "b".repeat(64),
                outcome = "APPROVED",
                checks = 12,
                findings = 2,
            )
            val line = File(directory, "integration-ledger.log").readLines().single()
            assertTrue(line.contains("EXTERNAL_VERIFICATION"))
            val detail = String(java.util.Base64.getDecoder().decode(line.substringAfterLast('\t')))
            assertTrue(detail.contains("VISLUMBOPS->HUBOPS"))
            assertTrue(detail.contains("outcome=APPROVED"))
            assertTrue(detail.contains("mutations=0"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun rejectsInvalidHashesAndOutcomes() {
        val directory = Files.createTempDirectory("hubops-ledger-invalid").toFile()
        try {
            val ledger = HubOpsOrchestrator(directory)
            val rejected = runCatching {
                ledger.recordExternalVerification(
                    EngineId.VISLUMBOPS, EngineId.HUBOPS, "id", "not-a-hash", "b".repeat(64),
                    "APPROVED", 0, 0
                )
            }.isFailure
            assertTrue(rejected)
        } finally {
            directory.deleteRecursively()
        }
    }
}
