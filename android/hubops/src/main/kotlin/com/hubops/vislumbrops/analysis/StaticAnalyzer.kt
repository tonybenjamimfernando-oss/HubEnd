package com.hubops.vislumbrops.analysis

import com.hubops.vislumbrops.core.*
import java.io.File
import java.util.zip.ZipFile

/**
 * Analisador estático: inspeciona o conteúdo binário dos ficheiros DEX
 * para detetar padrões suspeitos (reflexão, exec, WebView, criptografia fraca).
 * Não é um parser completo de DEX — é um scanner de strings robusto,
 * otimizado para performance mobile.
 */
class StaticAnalyzer {

    private data class Rule(
        val id: String,
        val category: CheckCategory,
        val severity: Severity,
        val patterns: List<Regex>,
        val title: String,
        val description: String,
        val cwe: String? = null,
        val owasp: String? = null,
        val hint: String
    )

    private val rules = listOf(
        Rule("REFLECTION", CheckCategory.STATIC_ANALYSIS, Severity.MEDIUM,
            listOf(Regex("Ljava/lang/reflect/Method;"),
                Regex("Ljava/lang/reflect/Field;")),
            "Uso intenso de reflexão",
            "Reflexão pode ser usada para evasão de análise.",
            "CWE-470", "M7",
            "Justificar uso; considerar substituir por chamadas diretas."),

        Rule("DYNAMIC_LOADING", CheckCategory.STATIC_ANALYSIS, Severity.HIGH,
            listOf(Regex("Ldalvik/system/DexClassLoader;"),
                Regex("Ldalvik/system/PathClassLoader;")),
            "Carregamento dinâmico de classes",
            "DexClassLoader/PathClassLoader carrega código em runtime.",
            "CWE-470", "M7",
            "Validar assinatura do código carregado."),

        Rule("EXEC", CheckCategory.STATIC_ANALYSIS, Severity.CRITICAL,
            listOf(Regex("Ljava/lang/Runtime;->exec")),
            "Execução de comandos de sistema",
            "Runtime.exec permite executar comandos shell.",
            "CWE-78", "M7",
            "Evitar; se necessário, validar input rigorosamente."),

        Rule("RAW_SQL", CheckCategory.STATIC_ANALYSIS, Severity.HIGH,
            listOf(Regex("Landroid/database/sqlite/SQLiteDatabase;->rawQuery")),
            "Uso de rawQuery em SQLite",
            "Susceptível a SQL injection se concatenar strings.",
            "CWE-89", "M7",
            "Usar selectionArgs / prepared statements."),

        Rule("WEAK_CRYPTO", CheckCategory.OWASP_MOBILE, Severity.HIGH,
            listOf(Regex("(DES|MD5|SHA-1|RC4)"),
                Regex("Cipher;->getInstance")),
            "Criptografia potencialmente fraca",
            "Detetados algoritmos DES/MD5/SHA-1/RC4.",
            "CWE-327", "M5",
            "Usar AES-GCM e SHA-256."),

        Rule("WEBVIEW_JS", CheckCategory.OWASP_MOBILE, Severity.HIGH,
            listOf(Regex("Landroid/webkit/WebView;->addJavascriptInterface")),
            "WebView com interface JavaScript exposta",
            "addJavascriptInterface permite chamadas Java a partir de JS.",
            "CWE-749", "M1",
            "Restringir a APIs controladas; usar WebViewClient."),

        Rule("SSL_BYPASS", CheckCategory.OWASP_MOBILE, Severity.CRITICAL,
            listOf(Regex("X509TrustManager"),
                Regex("HostnameVerifier"),
                Regex("TrustAllCerts")),
            "Bypass de validação SSL/TLS",
            "TrustManager inseguro permite MITM.",
            "CWE-295", "M3",
            "Implementar TrustManager correto e pinning."),

        Rule("DEBUGBUILD", CheckCategory.COMPLIANCE, Severity.MEDIUM,
            listOf(Regex("BuildConfig;->DEBUG")),
            "Verificação de BuildConfig.DEBUG",
            "Verificar se não expõe caminhos de debug em release.",
            null, null,
            "Confirmar que release não ativa debug."),

        Rule("ROOT_DETECT", CheckCategory.STATIC_ANALYSIS, Severity.LOW,
            listOf(Regex("/system/xbin/su"), Regex("/system/bin/su")),
            "Verificação de root presente",
            "Código verifica root — legítimo, mas auditar lógica.",
            null, null, "OK se for defesa."),

        Rule("LOG_SENSITIVE", CheckCategory.COMPLIANCE, Severity.MEDIUM,
            listOf(Regex("Landroid/util/Log;->d"),
                Regex("Landroid/util/Log;->v")),
            "Log de debug presente",
            "Log.d/Log.v podem expor dados em release.",
            null, null,
            "Envolver em if(BuildConfig.DEBUG).")
    )

    fun analyze(artifactPath: String): AnalysisResult {
        val findings = mutableListOf<Finding>()
        val matched = mutableListOf<String>()
        var dexCount = 0
        var skippedDex = 0
        var totalDexBytes = 0L

        try {
            ZipFile(File(artifactPath)).use { zip ->
                for (e in zip.entries()) {
                    if (!e.name.matches(Regex("classes\\d*\\.dex"))) continue
                    dexCount++
                    if (dexCount > 20 || e.size > 16L * 1024 * 1024 ||
                        (e.size >= 0 && totalDexBytes + e.size > 32L * 1024 * 1024)) {
                        skippedDex++
                        continue
                    }
                    val bytes = try {
                        zip.getInputStream(e).use { BoundedArchiveReader.read(it, 16 * 1024 * 1024) }
                    } catch (_: Exception) { null }
                    if (bytes == null) { skippedDex++; continue }
                    totalDexBytes += bytes.size
                    if (totalDexBytes > 32L * 1024 * 1024) { skippedDex++; continue }
                    val text = String(bytes, Charsets.ISO_8859_1)

                    for (rule in rules) {
                        val hits = rule.patterns.count { it.containsMatchIn(text) }
                        if (hits > 0) {
                            matched += rule.id
                            findings += Finding(
                                category = rule.category,
                                severity = rule.severity,
                                title = rule.title,
                                description = "${rule.description} ($hits ocorrências em ${e.name})",
                                location = e.name,
                                cweId = rule.cwe,
                                owaspId = rule.owasp,
                                remediationHint = rule.hint
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            return AnalysisResult(
                checks = listOf(VerificationCheck(
                    "STATIC-ERROR", CheckCategory.STATIC_ANALYSIS,
                    "Erro ao ler DEX", CheckResult.ERROR)),
                findings = emptyList()
            )
        }

        val result = when {
            findings.any { it.severity == Severity.CRITICAL } -> CheckResult.FAIL
            findings.any { it.severity == Severity.HIGH } -> CheckResult.WARN
            findings.isNotEmpty() || skippedDex > 0 -> CheckResult.WARN
            else -> CheckResult.PASS
        }

        val check = VerificationCheck(
            "STATIC-ANALYZE", CheckCategory.STATIC_ANALYSIS,
            "$dexCount DEX encontrados, ${matched.distinct().size} regras acionadas, $skippedDex ignorados por limite",
            result
        )
        if (skippedDex > 0) findings += Finding(
            category = CheckCategory.STATIC_ANALYSIS,
            severity = Severity.INFO,
            title = "Análise estática parcial",
            description = "$skippedDex ficheiros DEX excederam o limite de 16 MB e não foram analisados."
        )

        return AnalysisResult(listOf(check), findings)
    }
}
