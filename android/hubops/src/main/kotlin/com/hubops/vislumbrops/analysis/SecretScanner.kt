package com.hubops.vislumbrops.analysis

import com.hubops.vislumbrops.core.*
import java.io.File
import java.util.zip.ZipFile

class SecretScanner {

    private data class Pattern(
        val name: String, val regex: Regex, val severity: Severity,
        val cwe: String? = null
    )

    private val patterns = listOf(
        Pattern("AWS Access Key", Regex("AKIA[0-9A-Z]{16}"), Severity.CRITICAL, "CWE-798"),
        Pattern("AWS Secret", Regex("(?i)aws(.{0,20})?['\"][0-9a-zA-Z/+]{40}['\"]"), Severity.CRITICAL, "CWE-798"),
        Pattern("Google API Key", Regex("AIza[0-9A-Za-z\\-_]{35}"), Severity.HIGH, "CWE-798"),
        Pattern("Firebase URL", Regex("https://[a-z0-9-]+\\.firebaseio\\.com"), Severity.MEDIUM),
        Pattern("Stripe Live", Regex("sk_live_[0-9a-zA-Z]{24,}"), Severity.CRITICAL, "CWE-798"),
        Pattern("Stripe Test", Regex("sk_test_[0-9a-zA-Z]{24,}"), Severity.LOW),
        Pattern("JWT", Regex("eyJ[A-Za-z0-9_-]+\\.eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"), Severity.HIGH),
        Pattern("Private Key PEM", Regex("-----BEGIN (RSA |EC |DSA |OPENSSH |ENCRYPTED |)PRIVATE KEY-----"), Severity.CRITICAL),
        Pattern("GitHub Fine-grained Token", Regex("github_pat_[A-Za-z0-9_]{22,}"), Severity.CRITICAL, "CWE-798"),
        Pattern("Slack Webhook", Regex("https://hooks\\.slack\\.com/services/T[A-Z0-9]+/B[A-Z0-9]+/[A-Za-z0-9]{20,}"), Severity.HIGH, "CWE-798"),
        Pattern("Stripe Restricted Live", Regex("rk_live_[0-9a-zA-Z]{24,}"), Severity.CRITICAL, "CWE-798"),
        Pattern("Telegram Bot Token", Regex("\\b[0-9]{8,10}:[A-Za-z0-9_-]{35}\\b"), Severity.HIGH, "CWE-798"),
        Pattern("Anthropic API Key", Regex("sk-ant-[A-Za-z0-9_-]{20,}"), Severity.CRITICAL, "CWE-798"),
        Pattern("Mailgun Key", Regex("\\bkey-[0-9a-f]{32}\\b"), Severity.HIGH, "CWE-798"),
        Pattern("GitHub Token", Regex("gh[pousr]_[A-Za-z0-9]{36,}"), Severity.CRITICAL),
        Pattern("Slack Token", Regex("xox[baprs]-[0-9a-zA-Z]{10,}"), Severity.HIGH),
        Pattern("Bearer", Regex("(?i)bearer\\s+[A-Za-z0-9\\-._~+/]{20,}"), Severity.MEDIUM),
        Pattern("Password Assign", Regex("(?i)(password|passwd|pwd)\\s*[=:]\\s*['\"][^'\"]{6,}['\"]"), Severity.HIGH, "CWE-259"),
        Pattern("Google OAuth", Regex("ya29\\.[0-9A-Za-z\\-_]+"), Severity.HIGH),
        Pattern("Twilio", Regex("SK[0-9a-fA-F]{32}"), Severity.HIGH),
        Pattern("SendGrid", Regex("SG\\.[0-9A-Za-z\\-_]{22}\\.[0-9A-Za-z\\-_]{43}"), Severity.HIGH)
    )

    fun scan(artifactPath: String): AnalysisResult {
        val findings = mutableListOf<Finding>()
        var files = 0
        var highEntropy = 0
        var skippedFiles = 0
        var totalReadBytes = 0L
        var entriesSeen = 0

        try {
            ZipFile(File(artifactPath)).use { zip ->
                for (e in zip.entries()) {
                    if (entriesSeen++ >= 10_000) { skippedFiles++; break }
                    if (e.isDirectory || !isScannable(e.name)) continue
                    files++
                    if (e.size > 5L * 1024 * 1024) { skippedFiles++; continue }
                    val content = try {
                        zip.getInputStream(e).use { BoundedArchiveReader.read(it, 5 * 1024 * 1024) }
                    } catch (_: Exception) { null }
                    if (content == null) { skippedFiles++; continue }
                    if (totalReadBytes + content.size > 50L * 1024 * 1024) {
                        skippedFiles++
                        continue
                    }
                    totalReadBytes += content.size
                    val text = String(content, Charsets.ISO_8859_1)

                    for (p in patterns) {
                        p.regex.findAll(text).take(3).forEach { m ->
                            if (!isAllowlisted(m.value)) {
                                findings += Finding(
                                    category = CheckCategory.SECRETS,
                                    severity = p.severity,
                                    title = "Segredo: ${p.name}",
                                    description = "Padrão encontrado em ${e.name}",
                                    location = e.name,
                                    evidence = mask(m.value),
                                    cweId = p.cwe,
                                    remediationHint = "Remover do código; usar BuildConfig + Keystore."
                                )
                            }
                        }
                    }

                    if (e.name.endsWith(".dex") || e.name.endsWith(".so")) {
                        highEntropy += countEntropy(text)
                    }
                }
            }
        } catch (e: Exception) {
            return AnalysisResult(
                checks = listOf(VerificationCheck(
                    "SECRETS-ERROR", CheckCategory.SECRETS,
                    "Erro ao ler artefacto", CheckResult.ERROR)),
                findings = emptyList()
            )
        }

        if (highEntropy > 20) {
            findings += Finding(
                category = CheckCategory.SECRETS,
                severity = Severity.MEDIUM,
                title = "Alta densidade de strings de alta entropia",
                description = "$highEntropy blocos suspeitos",
                remediationHint = "Auditoria manual recomendada."
            )
        }

        if (skippedFiles > 0) findings += Finding(
            category = CheckCategory.SECRETS,
            severity = Severity.INFO,
            title = "Análise de segredos parcial",
            description = "$skippedFiles ficheiros excederam o limite de 5 MB ou o orçamento total e não foram analisados."
        )

        val result = when {
            findings.any { it.severity == Severity.CRITICAL } -> CheckResult.FAIL
            findings.isNotEmpty() || skippedFiles > 0 -> CheckResult.WARN
            else -> CheckResult.PASS
        }

        val check = VerificationCheck(
            "SECRETS-SCAN", CheckCategory.SECRETS,
            "$files ficheiros, ${findings.size} achados, $highEntropy blocos de alta entropia, $skippedFiles ignorados por limite",
            result
        )

        return AnalysisResult(listOf(check), findings)
    }

    private fun isScannable(n: String) =
        n.endsWith(".dex") || n.endsWith(".so") || n.endsWith(".xml") ||
                n.endsWith(".json") || n.startsWith("assets/") || n.startsWith("res/raw/")

    private fun isAllowlisted(v: String) = v.length < 12 ||
            v.contains("AKIAIOSFODNN7EXAMPLE") ||
            v.contains("sk_test_0000000000000000")

    private fun countEntropy(text: String): Int =
        Regex("[A-Za-z0-9+/_\\-=]{16,64}")
            .findAll(text)
            .count { CryptoProvider.shannonEntropy(it.value) > 4.5 }

    private fun mask(s: String) =
        if (s.length <= 8) "****" else s.take(4) + "…" + s.takeLast(2)
}
