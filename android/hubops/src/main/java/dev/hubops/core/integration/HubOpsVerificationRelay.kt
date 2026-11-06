package dev.hubops.core.integration

import com.hubops.vislumbrops.core.VerificationContract
import com.hubops.vislumbrops.hubops.HubOpsBridge
import com.hubops.vislumbrops.reporting.VislumbropsReport
import java.security.MessageDigest

/**
 * One-way, evidence-gated bridge from the autonomous VislumbOps engine into HubOps.
 * Results are appended to the common ledger; this adapter never changes project files.
 */
class HubOpsVerificationRelay(
    private val bridge: HubOpsBridge,
    private val ledger: HubOpsOrchestrator,
) {
    fun publish(contract: VerificationContract, report: VislumbropsReport): String {
        HubOpsEngineRegistry.requireFlow(EngineId.VISLUMBOPS, EngineId.HUBOPS, EngineFlowMode.EVIDENCE_ONLY)
        val classification = bridge.classify(contract)
        val outcome = when (classification) {
            is HubOpsBridge.OutboundResult.Approved -> "APPROVED"
            is HubOpsBridge.OutboundResult.RemediationRequired -> "REMEDIATION_REQUIRED"
            is HubOpsBridge.OutboundResult.Rejected -> "REJECTED"
        }
        val reportHash = sha256(report.markdownSummary)
        ledger.recordExternalVerification(
            sourceEngine = EngineId.VISLUMBOPS,
            destinationEngine = EngineId.HUBOPS,
            verificationId = contract.verificationId,
            artifactSha256 = contract.artifactHash,
            reportSha256 = reportHash,
            outcome = outcome,
            checks = contract.checks.size,
            findings = contract.findings.size,
        )
        return outcome
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
