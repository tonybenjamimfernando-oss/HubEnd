package com.hubops.vislumbrops.hubops

import com.hubops.vislumbrops.core.*
import com.hubops.vislumbrops.certification.CertificationAuthority
import com.hubops.vislumbrops.certification.EvidenceChain

class HubOpsBridge(
    private val evidenceChain: EvidenceChain = EvidenceChain(),
    private val certificationAuthority: CertificationAuthority? = null
) {

    sealed class InboundRequest {
        data class VerifyArtifact(val projectId: String, val artifactPath: String) : InboundRequest()
        data class VerifyContract(val contract: VerificationContract) : InboundRequest()
    }

    sealed class OutboundResult {
        data class Approved(val contract: VerificationContract) : OutboundResult()
        data class RemediationRequired(val contract: VerificationContract,
                                       val plan: RemediationPlan) : OutboundResult()
        data class Rejected(val contract: VerificationContract) : OutboundResult()
    }

    fun classify(contract: VerificationContract): OutboundResult = when (contract.status) {
        VerificationStatus.PASSED, VerificationStatus.PASSED_WITH_WARNINGS ->
            if (validateIncoming(contract)) OutboundResult.Approved(contract)
            else OutboundResult.Rejected(contract)
        VerificationStatus.FAILED -> contract.remediationPlan?.let {
            OutboundResult.RemediationRequired(contract, it)
        } ?: OutboundResult.Rejected(contract)
        else -> OutboundResult.Rejected(contract)
    }

    fun validateIncoming(contract: VerificationContract): Boolean =
        contract.contractVersion == "1.0.0" &&
            contract.artifactHash.matches(Regex("[0-9a-f]{64}")) &&
            contract.evidenceChain.isNotEmpty() &&
            evidenceChain.verifyChain(contract.evidenceChain) &&
            certificationAuthority?.verify(contract) == true &&
            (contract.status == VerificationStatus.PASSED ||
                contract.status == VerificationStatus.PASSED_WITH_WARNINGS)
}
