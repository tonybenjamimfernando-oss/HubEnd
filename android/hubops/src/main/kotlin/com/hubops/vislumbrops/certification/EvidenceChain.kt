package com.hubops.vislumbrops.certification

import com.hubops.vislumbrops.core.*

class EvidenceChain {
    fun build(contract: VerificationContract): List<Evidence> {
        val chain = mutableListOf<Evidence>()
        var previous = "GENESIS"
        fun append(type: String, payload: String) {
            val hash = CryptoProvider.sha256("$previous|$type|$payload")
            chain += Evidence(type = type, payload = payload, hash = hash)
            previous = hash
        }
        append("ARTIFACT_SHA256", contract.artifactHash)
        append("FINAL_STATUS", contract.status.name)
        contract.checks.sortedBy { it.id }.forEach {
            append("CHECK", "${it.id}|${it.category}|${it.result}|${it.durationMs}|${it.description}")
        }
        contract.findings.sortedBy { it.id }.forEach {
            append("FINDING", "${it.id}|${it.severity}|${it.category}|${it.title}|${it.location.orEmpty()}")
        }
        contract.metadata.toSortedMap().forEach { (key, value) -> append("METADATA", "$key=$value") }
        contract.score?.let {
            append("SCORE", "${it.overall}|${it.security}|${it.stability}|${it.performance}|${it.compliance}|${it.grade}")
        }
        append("COMPLETED_AT", contract.completedAt ?: "unknown")
        return chain
    }

    fun verifyChain(chain: List<Evidence>): Boolean {
        var previous = "GENESIS"
        for (evidence in chain) {
            val expected = CryptoProvider.sha256("$previous|${evidence.type}|${evidence.payload}")
            if (!CryptoProvider.constantTimeEquals(evidence.hash, expected)) return false
            previous = evidence.hash
        }
        return true
    }
}
