package dev.hubops.core.integration

import dev.hubops.despertar.project.ProjectInsight
import dev.hubops.despertar.project.ProjectIntelligence
import com.hubops.vislumbrops.core.CheckCategory
import com.hubops.vislumbrops.core.CheckResult
import com.hubops.vislumbrops.core.Finding
import com.hubops.vislumbrops.core.Severity
import com.hubops.vislumbrops.core.VerificationCheck
import com.hubops.vislumbrops.core.VerificationContract
import com.hubops.vislumbrops.core.VerificationStatus
import java.io.File
import java.security.MessageDigest

/**
 * Read-only join of Espelhar-X source analysis and a completed VislumbOps APK audit.
 * The buildReference is supplied by the caller: the coordinator never assumes that
 * a source ZIP and an APK are identical artefacts merely because they share a name.
 */
data class CrossEngineAssessment(
    val buildReference: String,
    val sourceArchiveName: String,
    val sourceArchiveSha256: String,
    val binaryArtifactPath: String,
    val binaryArtifactSha256: String,
    val vislumbVerificationId: String,
    val vislumbStatus: VerificationStatus,
    val astFilesConsidered: Int,
    val astFilesParsed: Int,
    val astFilesUnavailable: Int,
    val astFilesWithSyntaxErrors: Int,
    val astFilesTruncated: Int,
    val findings: List<Finding>,
    val checks: List<VerificationCheck>,
    val evidenceSha256: String,
    val limitations: List<String>,
) {
    val requiresReview: Boolean get() = findings.any { it.severity == Severity.CRITICAL || it.severity == Severity.HIGH } ||
        checks.any { it.result == CheckResult.ERROR || it.result == CheckResult.FAIL } ||
        checks.any { it.id in setOf("ESPELHAR-AST-COVERAGE", "CROSS-ENGINE-PROVENANCE") && it.result != CheckResult.PASS }
}

class CrossEngineAssessmentCoordinator(
    private val analyzeSource: (File) -> ProjectInsight = ProjectIntelligence::analyzeZip,
) {
    /** No project, APK, staging, or ledger files are modified by this operation. */
    fun assess(
        sourceArchive: File,
        buildReference: String,
        vislumbContract: VerificationContract,
    ): CrossEngineAssessment {
        require(buildReference.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"))) {
            "Referência de build inválida. Use 1–128 caracteres alfanuméricos, ponto, sublinhado, dois-pontos ou hífen."
        }
        require(vislumbContract.status !in setOf(VerificationStatus.PENDING, VerificationStatus.RUNNING)) {
            "A auditoria VislumbOps ainda não terminou."
        }
        require(vislumbContract.artifactHash.matches(Regex("(?i)[a-f0-9]{64}"))) {
            "O contrato VislumbOps não contém um SHA-256 válido do artefacto binário."
        }
        require(vislumbContract.artifactPath.isNotBlank()) { "O contrato VislumbOps não identifica o artefacto binário." }
        val source = sourceArchive.canonicalFile
        require(source.isFile && source.length() in 1..(30L * 1024 * 1024)) {
            "Arquivo-fonte inexistente, vazio ou superior a 30 MB."
        }
        val sourceHashBefore = sha256(source)
        val insight = analyzeSource(source)
        require(sha256(source) == sourceHashBefore) { "O arquivo-fonte mudou durante a análise; repita a avaliação." }

        val findings = mutableListOf<Finding>()
        findings += vislumbContract.findings
        val ast = insight.astReport
        ast.fileResults.filter { it.hasSyntaxErrors }.forEach { file ->
            findings += Finding(
                category = CheckCategory.STATIC_ANALYSIS,
                severity = Severity.HIGH,
                title = "Erros sintáticos detetados pela AST",
                description = "Tree-sitter encontrou ${file.syntaxErrorNodes} nós de erro. Confirmar com o compilador oficial.",
                location = file.path,
                evidence = "language=${file.language};nodes=${file.nodeCount};errors=${file.syntaxErrorNodes}",
                remediationHint = "Rever o ficheiro e executar a compilação/testes oficiais antes de aprovar qualquer correção."
            )
        }
        insight.findings.filter { it.severity != "INFO" }.forEach { item ->
            val severity = when (item.severity.uppercase()) {
                "CRÍTICO", "CRITICO" -> Severity.CRITICAL
                "ALTO" -> Severity.HIGH
                "MÉDIO", "MEDIO" -> Severity.MEDIUM
                "BAIXO" -> Severity.LOW
                else -> Severity.INFO
            }
            findings += Finding(
                category = CheckCategory.STATIC_ANALYSIS,
                severity = severity,
                title = item.title,
                description = item.detail,
                location = null,
                remediationHint = "Rever manualmente; os alertas heurísticos não autorizam alterações automáticas."
            )
        }

        val checks = mutableListOf<VerificationCheck>()
        checks += vislumbContract.checks
        checks += VerificationCheck(
            id = "ESPELHAR-AST-COVERAGE",
            category = CheckCategory.STATIC_ANALYSIS,
            description = "AST Tree-sitter: ${ast.filesParsed}/${ast.filesConsidered} analisados; ${ast.filesUnavailable} indisponíveis; ${ast.filesOmitted} omitidos.",
            result = when {
                ast.filesWithSyntaxErrors > 0 -> CheckResult.FAIL
                ast.filesUnavailable > 0 || ast.filesOmitted > 0 || ast.truncatedFiles > 0 -> CheckResult.WARN
                ast.filesConsidered == 0 -> CheckResult.SKIPPED
                else -> CheckResult.PASS
            }
        )
        checks += VerificationCheck(
            id = "CROSS-ENGINE-PROVENANCE",
            category = CheckCategory.ARTIFACT_INTEGRITY,
            description = "Build $buildReference associado por referência declarativa a source SHA-256 e APK SHA-256; atestação assinada ainda não verificada.",
            result = CheckResult.WARN
        )

        val limitations = buildList {
            add("Proveniência ainda não verificada dentro da aplicação: a referência de build é declarada pelo chamador e não prova que o APK foi compilado a partir deste ZIP. Exige atestação assinada e verificação externa.")
            add("Tree-sitter deteta estrutura sintática, não substitui compilador, testes, análise semântica ou execução.")
            add("Os achados do VislumbOps mantêm o âmbito e as limitações do contrato original.")
            if (ast.filesUnavailable > 0) addAll(ast.unavailableReasons.distinct())
            if (ast.filesOmitted > 0) add("${ast.filesOmitted} ficheiros foram omitidos por limites da análise AST.")
        }
        val orderedFindings = findings.distinctBy { listOf(it.category.name, it.title, it.location.orEmpty(), it.description) }
            .sortedWith(compareByDescending<Finding> { it.severity.ordinal }.thenBy { it.title }.thenBy { it.location.orEmpty() })
        val evidence = buildString {
            append(buildReference).append('|').append(sourceHashBefore.lowercase()).append('|')
            append(vislumbContract.artifactHash.lowercase()).append('|').append(vislumbContract.verificationId).append('|')
            append(vislumbContract.status.name).append('|').append(ast.filesConsidered).append('|')
            append(ast.filesParsed).append('|').append(ast.filesUnavailable).append('|')
            append(ast.filesWithSyntaxErrors).append('|').append(ast.truncatedFiles).append('|')
            orderedFindings.forEach { append(it.category.name).append(':').append(it.severity.name).append(':').append(it.title).append(':').append(it.location.orEmpty()).append(';') }
        }
        return CrossEngineAssessment(
            buildReference = buildReference,
            sourceArchiveName = source.name,
            sourceArchiveSha256 = sourceHashBefore,
            binaryArtifactPath = vislumbContract.artifactPath,
            binaryArtifactSha256 = vislumbContract.artifactHash.lowercase(),
            vislumbVerificationId = vislumbContract.verificationId,
            vislumbStatus = vislumbContract.status,
            astFilesConsidered = ast.filesConsidered,
            astFilesParsed = ast.filesParsed,
            astFilesUnavailable = ast.filesUnavailable,
            astFilesWithSyntaxErrors = ast.filesWithSyntaxErrors,
            astFilesTruncated = ast.truncatedFiles,
            findings = orderedFindings,
            checks = checks,
            evidenceSha256 = sha256(evidence.toByteArray(Charsets.UTF_8)),
            limitations = limitations.distinct(),
        )
    }

    private fun sha256(file: File): String = file.inputStream().buffered().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
