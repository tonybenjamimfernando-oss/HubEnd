package dev.hubops.core.integration

import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Regression tests: external verification may write evidence only across registered evidence flows. */
class ExternalVerificationFlowPolicyTest {
    private val artifactHash = "a".repeat(64)
    private val reportHash = "b".repeat(64)

    @Test
    fun rejectsUnregisteredOrMutationFlowBeforeWritingLedger() {
        val state = Files.createTempDirectory("hubend-policy-").toFile()
        try {
            val orchestrator = HubOpsOrchestrator(state)
            try {
                orchestrator.recordExternalVerification(
                    sourceEngine = EngineId.DESPERTAR,
                    destinationEngine = EngineId.HUBOPS,
                    verificationId = "verification-unauthorized",
                    artifactSha256 = artifactHash,
                    reportSha256 = reportHash,
                    outcome = "APPROVED",
                    checks = 3,
                    findings = 0,
                )
                fail("A flow without EVIDENCE_ONLY permission must be rejected.")
            } catch (_: IllegalArgumentException) {
                // Expected: the registry is the authority for cross-engine evidence flows.
            }
            assertFalse("Rejected evidence must not reach the ledger.", File(state, "integration-ledger.log").exists())
        } finally {
            state.deleteRecursively()
        }
    }

    @Test
    fun acceptsRegisteredVislumbOpsEvidenceFlow() {
        val state = Files.createTempDirectory("hubend-policy-").toFile()
        try {
            val orchestrator = HubOpsOrchestrator(state)
            orchestrator.recordExternalVerification(
                sourceEngine = EngineId.VISLUMBOPS,
                destinationEngine = EngineId.HUBOPS,
                verificationId = "verification-authorized",
                artifactSha256 = artifactHash,
                reportSha256 = reportHash,
                outcome = "APPROVED",
                checks = 3,
                findings = 0,
            )
        } finally {
            state.deleteRecursively()
        }
    }
}
