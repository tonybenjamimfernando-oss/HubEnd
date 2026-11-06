package com.hubops.vislumbrops.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

@Serializable
data class VerificationContract(
    val contractVersion: String = "1.0.0",
    val verificationId: String = UUID.randomUUID().toString(),
    val projectId: String,
    val artifactPath: String,
    val artifactHash: String = "",
    val source: Engine = Engine.VISLUMBROPS,
    val requestedBy: Engine = Engine.HUBOPS,
    val requestedAt: String = Instant.now().toString(),
    val completedAt: String? = null,
    val status: VerificationStatus = VerificationStatus.PENDING,
    val checks: List<VerificationCheck> = emptyList(),
    val findings: List<Finding> = emptyList(),
    val evidenceChain: List<Evidence> = emptyList(),
    val score: VerificationScore? = null,
    val certificate: Certificate? = null,
    val remediationPlan: RemediationPlan? = null,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
enum class Engine { DESPERTAR, ESPELHAR_X, VISLUMBROPS, HUBOPS }

@Serializable
enum class VerificationStatus {
    PENDING, RUNNING, PASSED, PASSED_WITH_WARNINGS, FAILED, ERROR, CANCELLED
}

@Serializable
enum class Severity { INFO, LOW, MEDIUM, HIGH, CRITICAL }

@Serializable
enum class CheckCategory {
    ARTIFACT_INTEGRITY, SUPPLY_CHAIN, SECRETS, PERMISSIONS, DEPENDENCIES,
    STATIC_ANALYSIS, RUNTIME_STABILITY, PERFORMANCE,
    ACCESSIBILITY, COMPLIANCE, OWASP_MOBILE
}

@Serializable
enum class CheckResult { PASS, WARN, FAIL, SKIPPED, ERROR }

@Serializable
data class VerificationCheck(
    val id: String,
    val category: CheckCategory,
    val description: String,
    val result: CheckResult,
    val durationMs: Long = 0L
)

@Serializable
data class Finding(
    val id: String = UUID.randomUUID().toString(),
    val category: CheckCategory,
    val severity: Severity,
    val title: String,
    val description: String,
    val location: String? = null,
    val evidence: String? = null,
    val cweId: String? = null,
    val owaspId: String? = null,
    val remediationHint: String? = null
)

@Serializable
data class Evidence(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val payload: String,
    val hash: String,
    val timestamp: String = Instant.now().toString()
)

@Serializable
data class VerificationScore(
    val overall: Int,
    val security: Int,
    val stability: Int,
    val performance: Int,
    val compliance: Int,
    val grade: Grade
) {
    @Serializable
    enum class Grade { GOLD, SILVER, BRONZE, REJECTED }
}

@Serializable
data class Certificate(
    val certificateId: String,
    val issuedAt: String,
    val validUntil: String,
    val signature: String,
    val grade: VerificationScore.Grade,
    val issuer: String = "HubOps Certification Authority"
)

@Serializable
data class RemediationPlan(
    val targetEngine: Engine,
    val steps: List<RemediationStep>
)

@Serializable
data class RemediationStep(
    val order: Int,
    val action: String,
    val addressesFindingIds: List<String>,
    val estimatedEffort: String
)

data class AnalysisResult(
    val checks: List<VerificationCheck> = emptyList(),
    val findings: List<Finding> = emptyList()
)
