package com.hubops.vislumbrops.core

import com.hubops.vislumbrops.analysis.*
import com.hubops.vislumbrops.certification.CertificationAuthority
import com.hubops.vislumbrops.certification.EvidenceChain
import com.hubops.vislumbrops.certification.RemediationPlanner
import com.hubops.vislumbrops.reporting.ReportGenerator
import com.hubops.vislumbrops.reporting.VislumbropsReport
import com.hubops.vislumbrops.runtime.SandboxRunner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant

class VerificationOrchestrator(
    private val inspector: ApkInspector,
    private val signatureEngine: ApkSignatureVerificationEngine,
    private val supplyChainEngine: ArtifactSupplyChainEngine,
    private val secretScanner: SecretScanner,
    private val dependencyAuditor: DependencyAuditor,
    private val permissionAnalyzer: PermissionAnalyzer,
    private val staticAnalyzer: StaticAnalyzer,
    private val sandboxRunner: SandboxRunner,
    private val certAuthority: CertificationAuthority,
    private val evidenceChain: EvidenceChain,
    private val remediationPlanner: RemediationPlanner,
    private val reportGenerator: ReportGenerator
) {

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
                CoroutineName("Vislumbrops-Orchestrator")
    )

    fun verify(
        contract: VerificationContract,
        timeBudgetMs: Long = 8 * 60 * 1000,
        enableRuntimeSandbox: Boolean = false
    ): Flow<VerificationEvent> = flow {
        val start = System.currentTimeMillis()
        var current = contract.copy(
            status = VerificationStatus.RUNNING,
            completedAt = null
        )
        emit(VerificationEvent.Started(current))

        try {
            // Fase 1: integridade
            emit(VerificationEvent.Phase("ARTIFACT_INTEGRITY"))
            val integrityChecks = inspector.verifyIntegrity(contract.artifactPath)
            val signatureAudit = withContext(Dispatchers.IO) { signatureEngine.verify(contract.artifactPath) }
            val hash = try {
                computeFileHash(contract.artifactPath)
            } catch (_: Exception) { "" }
            val hashCheck = VerificationCheck(
                "APK-SHA256",
                CheckCategory.ARTIFACT_INTEGRITY,
                "SHA-256 do artefacto calculado",
                if (hash.matches(Regex("[0-9a-f]{64}"))) CheckResult.PASS else CheckResult.ERROR
            )
            current = current.copy(
                checks = current.checks + integrityChecks + signatureAudit.checks + hashCheck,
                artifactHash = hash,
                metadata = current.metadata + listOfNotNull(
                    signatureAudit.packageName?.let { "apk.packageName" to it },
                    signatureAudit.errorCode?.let { "apk.signature.error" to it },
                    signatureAudit.multipleSigners?.let { "apk.signature.multipleSigners" to it.toString() },
                    if (signatureAudit.signerSha256.isNotEmpty()) "apk.signature.certificatesSha256" to signatureAudit.signerSha256.joinToString(",") else null
                )
            )
            emit(VerificationEvent.PartialUpdate(current))

            // Fase 2: análise paralela
            emit(VerificationEvent.Phase("PARALLEL_ANALYSIS"))
            val (results, supplyAudit) = withContext(Dispatchers.Default) {
                val analyses = listOf(
                    async { runCatching { secretScanner.scan(contract.artifactPath) }
                        .getOrElse { AnalysisResult(checks = listOf(VerificationCheck(
                            "SECRETS-UNAVAILABLE", CheckCategory.SECRETS,
                            "Scanner de segredos indisponível", CheckResult.ERROR))) } },
                    async { runCatching { dependencyAuditor.audit(contract.artifactPath) }
                        .getOrElse { AnalysisResult(checks = listOf(VerificationCheck(
                            "DEPS-UNAVAILABLE", CheckCategory.DEPENDENCIES,
                            "Auditoria de dependências indisponível", CheckResult.ERROR))) } },
                    async { runCatching { permissionAnalyzer.analyze(contract.artifactPath) }
                        .getOrElse { AnalysisResult(checks = listOf(VerificationCheck(
                            "PERM-UNAVAILABLE", CheckCategory.PERMISSIONS,
                            "Análise de permissões indisponível", CheckResult.ERROR))) } },
                    async { runCatching { staticAnalyzer.analyze(contract.artifactPath) }
                        .getOrElse { AnalysisResult(checks = listOf(VerificationCheck(
                            "STATIC-UNAVAILABLE", CheckCategory.STATIC_ANALYSIS,
                            "Análise estática indisponível", CheckResult.ERROR))) } }
                ).awaitAll()
                val supply = runCatching { supplyChainEngine.audit(contract.artifactPath) }
                    .getOrElse { ArtifactSupplyChainEngine.Audit(
                        AnalysisResult(checks = listOf(VerificationCheck(
                            "SUPPLY-UNAVAILABLE", CheckCategory.SUPPLY_CHAIN,
                            "Auditoria de cadeia de fornecimento indisponível", CheckResult.ERROR))), null) }
                analyses to supply
            }

            current = current.copy(
                checks = current.checks + results.flatMap { it.checks } + supplyAudit.result.checks,
                findings = current.findings + results.flatMap { it.findings } + supplyAudit.result.findings,
                metadata = current.metadata + listOfNotNull(
                    supplyAudit.manifest?.let { "supplyChain.manifestSha256" to it.manifestSha256 },
                    supplyAudit.manifest?.let { "supplyChain.entryCount" to it.entryCount.toString() },
                    supplyAudit.manifest?.let { "supplyChain.artifactSha256" to it.artifactSha256 },
                    supplyAudit.manifest?.let { "supplyChain.nativeLibraries" to it.nativeLibraries.joinToString(",") },
                    supplyAudit.manifest?.let { "supplyChain.dexFiles" to it.dexFiles.joinToString(",") }
                ).toMap()
            )
            emit(VerificationEvent.PartialUpdate(current))

            // Fase 3: runtime lab. Disabled by default: this app must never install
            // or uninstall an APK on the user's device as a side effect of verification.
            emit(VerificationEvent.Phase("RUNTIME_SANDBOX"))
            if (enableRuntimeSandbox) {
                val runtime = withTimeoutOrNull((timeBudgetMs / 3).coerceAtLeast(1L)) {
                    sandboxRunner.execute(contract.artifactPath)
                }
                if (runtime != null) {
                    current = current.copy(
                        checks = current.checks + runtime.checks,
                        findings = current.findings + runtime.findings
                    )
                    emit(VerificationEvent.PartialUpdate(current))
                } else {
                    current = current.copy(checks = current.checks + VerificationCheck(
                        "RUNTIME-LAB-TIMEOUT", CheckCategory.RUNTIME_STABILITY,
                        "Laboratório de runtime excedeu o orçamento", CheckResult.ERROR
                    ))
                    emit(VerificationEvent.Warning("Laboratório de runtime excedeu o orçamento"))
                }
            } else {
                current = current.copy(checks = current.checks + VerificationCheck(
                    "RUNTIME-LAB-NOT-CONFIGURED", CheckCategory.RUNTIME_STABILITY,
                    "Teste dinâmico não executado: requer laboratório externo explicitamente configurado",
                    CheckResult.SKIPPED
                ))
            }
            emit(VerificationEvent.PartialUpdate(current))

            // Fase 4: score
            emit(VerificationEvent.Phase("SCORING"))
            val score = calculateScore(current)
            current = current.copy(score = score)

            val structurallyValid = current.artifactHash.isNotBlank() &&
                current.checks.any { it.id == "APK-ZIP-VALID" && it.result == CheckResult.PASS }
            val hasCritical = current.findings.any { it.severity == Severity.CRITICAL }
            val finalStatus = when {
                !structurallyValid -> VerificationStatus.ERROR
                score.overall >= 85 && !hasCritical -> VerificationStatus.PASSED
                score.overall >= 60 && !hasCritical -> VerificationStatus.PASSED_WITH_WARNINGS
                else -> VerificationStatus.FAILED
            }
            current = current.copy(
                status = finalStatus,
                completedAt = Instant.now().toString()
            )

            // Fase 5: evidências — incluem estado final, verificações e metadados.
            emit(VerificationEvent.Phase("EVIDENCE_CHAIN"))
            val evidences = evidenceChain.build(current)
            current = current.copy(evidenceChain = evidences)

            // Fase 6: atestação local — só para artefacto estruturalmente válido e sem achados críticos.
            emit(VerificationEvent.Phase("CERTIFICATION"))
            val certificate = if (score.overall >= 60 && structurallyValid && !hasCritical)
                certAuthority.issue(current) else null
            val remediation = if (finalStatus == VerificationStatus.FAILED ||
                finalStatus == VerificationStatus.ERROR) remediationPlanner.plan(current) else null

            current = current.copy(
                certificate = certificate,
                remediationPlan = remediation
            )

            // Fase 7: relatório
            emit(VerificationEvent.Phase("REPORT"))
            val report = reportGenerator.generate(current)
            emit(VerificationEvent.Completed(report, current))

        } catch (e: CancellationException) {
            emit(VerificationEvent.Cancelled(contract.verificationId))
            throw e
        } catch (e: Exception) {
            emit(VerificationEvent.Failed(contract.verificationId, e))
        }
    }

    private fun calculateScore(c: VerificationContract): VerificationScore {
        fun scoreFor(cat: CheckCategory): Int {
            val checks = c.checks.filter { it.category == cat }
            if (checks.isEmpty()) return 50 // ausência de evidência nunca equivale a aprovação
            val points = checks.sumOf {
                when (it.result) {
                    CheckResult.PASS -> 100
                    CheckResult.WARN -> 60
                    CheckResult.SKIPPED -> 40
                    CheckResult.FAIL, CheckResult.ERROR -> 0
                }
            }
            return (points / checks.size).coerceIn(0, 100)
        }

        val security = listOf(
            CheckCategory.SECRETS, CheckCategory.PERMISSIONS,
            CheckCategory.OWASP_MOBILE, CheckCategory.STATIC_ANALYSIS
        ).map(::scoreFor).average().toInt()
        val stability = scoreFor(CheckCategory.RUNTIME_STABILITY)
        val performance = scoreFor(CheckCategory.PERFORMANCE)
        val compliance = listOf(
            CheckCategory.ARTIFACT_INTEGRITY, CheckCategory.COMPLIANCE,
            CheckCategory.DEPENDENCIES, CheckCategory.SUPPLY_CHAIN
        ).map(::scoreFor).average().toInt()
        val overall = (security * 4 + stability * 3 + performance * 2 + compliance) / 10
        val grade = when {
            overall >= 90 -> VerificationScore.Grade.GOLD
            overall >= 75 -> VerificationScore.Grade.SILVER
            overall >= 60 -> VerificationScore.Grade.BRONZE
            else -> VerificationScore.Grade.REJECTED
        }
        return VerificationScore(overall, security, stability, performance, compliance, grade)
    }

    private fun computeFileHash(path: String): String {
        val file = java.io.File(path)
        if (!file.isFile || !file.canRead() || file.length() > 100L * 1024 * 1024) return ""
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            var read: Int
            var total = 0L
            while (input.read(buf).also { read = it } > 0) {
                total += read
                if (total > 100L * 1024 * 1024) return ""
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun shutdown() = scope.cancel()
}

sealed class VerificationEvent {
    data class Started(val contract: VerificationContract) : VerificationEvent()
    data class Phase(val name: String) : VerificationEvent()
    data class PartialUpdate(val contract: VerificationContract) : VerificationEvent()
    data class Warning(val message: String) : VerificationEvent()
    data class Completed(val report: VislumbropsReport,
                         val contract: VerificationContract) : VerificationEvent()
    data class Failed(val id: String, val cause: Throwable) : VerificationEvent()
    data class Cancelled(val id: String) : VerificationEvent()
}
