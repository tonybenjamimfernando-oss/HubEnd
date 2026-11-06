package com.hubops.vislumbrops.reporting

import com.hubops.vislumbrops.core.*
import org.json.JSONArray
import org.json.JSONObject

class ReportGenerator {

    fun generate(contract: VerificationContract): VislumbropsReport =
        VislumbropsReport(
            contract = contract,
            htmlReport = toHtml(contract),
            jsonReport = toJson(contract),
            markdownSummary = toMarkdown(contract)
        )

    private fun toJson(c: VerificationContract): String {
        val root = JSONObject().apply {
            put("contractVersion", c.contractVersion)
            put("verificationId", c.verificationId)
            put("projectId", c.projectId)
            put("artifactHash", c.artifactHash)
            put("status", c.status.name)
            put("completedAt", c.completedAt ?: JSONObject.NULL)
            put("metadata", JSONObject().apply {
                c.metadata.toSortedMap().forEach { (key, value) -> put(key, value) }
            })
            put("score", c.score?.let {
                JSONObject().apply {
                    put("overall", it.overall)
                    put("security", it.security)
                    put("stability", it.stability)
                    put("performance", it.performance)
                    put("compliance", it.compliance)
                    put("grade", it.grade.name)
                }
            } ?: JSONObject.NULL)
            put("checks", JSONArray().apply {
                c.checks.forEach {
                    put(JSONObject().apply {
                        put("id", it.id); put("category", it.category.name)
                        put("result", it.result.name); put("description", it.description)
                    })
                }
            })
            put("findings", JSONArray().apply {
                c.findings.forEach {
                    put(JSONObject().apply {
                        put("id", it.id); put("severity", it.severity.name)
                        put("category", it.category.name); put("title", it.title)
                        put("description", it.description)
                        put("location", it.location ?: JSONObject.NULL)
                        put("remediationHint", it.remediationHint ?: JSONObject.NULL)
                    })
                }
            })
            put("certificate", c.certificate?.let {
                JSONObject().apply {
                    put("id", it.certificateId); put("grade", it.grade.name)
                    put("signature", it.signature); put("issuedAt", it.issuedAt)
                    put("validUntil", it.validUntil); put("issuer", it.issuer)
                }
            } ?: JSONObject.NULL)
            put("evidenceChain", JSONArray().apply {
                c.evidenceChain.forEach { put(JSONObject().apply {
                    put("id", it.id); put("type", it.type); put("payload", it.payload)
                    put("hash", it.hash); put("timestamp", it.timestamp)
                }) }
            })
        }
        return root.toString(2)
    }

    private fun toMarkdown(c: VerificationContract): String = buildString {
        appendLine("# Relatório Vislumbrops")
        appendLine()
        appendLine("**Projeto:** ${c.projectId}")
        appendLine("**Verificação:** ${c.verificationId}")
        appendLine("**Status:** ${c.status}")
        appendLine("**Artefacto (SHA-256):** `${c.artifactHash}`")
        appendLine()
        c.score?.let {
            appendLine("## Score")
            appendLine("| Dimensão | Valor |")
            appendLine("|----------|-------|")
            appendLine("| **Geral** | **${it.overall}/100 (${it.grade})** |")
            appendLine("| Segurança | ${it.security} |")
            appendLine("| Estabilidade | ${it.stability} |")
            appendLine("| Performance | ${it.performance} |")
            appendLine("| Conformidade | ${it.compliance} |")
            appendLine()
        }
        c.metadata.filterKeys { it.startsWith("supplyChain.") }.toSortedMap().takeIf { it.isNotEmpty() }?.let { metadata ->
            appendLine("## Cadeia de fornecimento")
            metadata.forEach { (key, value) -> appendLine("- **${key.removePrefix("supplyChain.")}:** `$value`") }
            appendLine("- **Nota:** manifesto de inventário local; não constitui SBOM completo nem validação de assinatura.")
            appendLine()
        }
        if (c.findings.isNotEmpty()) {
            appendLine("## Achados (${c.findings.size})")
            c.findings.sortedByDescending { it.severity.ordinal }.forEach {
                appendLine("- **[${it.severity}]** ${it.title}")
                appendLine("  - ${it.description}")
                it.remediationHint?.let { h -> appendLine("  - _Sugestão:_ $h") }
            }
            appendLine()
        }
        c.remediationPlan?.let { plan ->
            appendLine("## Plano de Remediação (destino: ${plan.targetEngine})")
            plan.steps.forEach { appendLine("${it.order}. **${it.estimatedEffort}** — ${it.action}") }
        }
        c.certificate?.let {
            appendLine()
            appendLine("## Certificado")
            appendLine("- **ID:** ${it.certificateId}")
            appendLine("- **Grau:** ${it.grade}")
            appendLine("- **Válido até:** ${it.validUntil}")
            appendLine("- **Emissor:** ${it.issuer}")
            appendLine("- **Assinatura local:** `${it.signature}`")
            appendLine("- **Nota:** atestação local protegida pelo Android Keystore; não é certificado público.")
        }
    }

    private fun toHtml(c: VerificationContract): String = buildString {
        appendLine("<!DOCTYPE html><html><head><meta charset='utf-8'>")
        appendLine("<title>Vislumbrops — ${html(c.projectId)}</title>")
        appendLine("<style>body{font-family:system-ui;max-width:900px;margin:2em auto;padding:1em;}")
        appendLine(".gold{color:#b8860b}.silver{color:#666}.bronze{color:#cd7f32}.rejected{color:#c00}")
        appendLine("table{border-collapse:collapse;width:100%}td,th{border:1px solid #ddd;padding:6px}")
        appendLine("</style></head><body>")
        appendLine("<h1>🌌 Relatório Vislumbrops</h1>")
        appendLine("<p><b>Projeto:</b> ${html(c.projectId)}<br>")
        appendLine("<b>Status:</b> ${html(c.status.name)}<br>")
        appendLine("<b>Hash:</b> <code>${html(c.artifactHash)}</code></p>")
        c.score?.let {
            appendLine("<h2>Score</h2><table>")
            appendLine("<tr><th>Geral</th><td class='${it.grade.name.lowercase()}'>")
            appendLine("<b>${it.overall}/100 — ${it.grade}</b></td></tr>")
            appendLine("<tr><th>Segurança</th><td>${it.security}</td></tr>")
            appendLine("<tr><th>Estabilidade</th><td>${it.stability}</td></tr>")
            appendLine("<tr><th>Performance</th><td>${it.performance}</td></tr>")
            appendLine("<tr><th>Conformidade</th><td>${it.compliance}</td></tr></table>")
        }
        if (c.findings.isNotEmpty()) {
            appendLine("<h2>Achados</h2><ul>")
            c.findings.sortedByDescending { it.severity.ordinal }.forEach {
                appendLine("<li><b>[${html(it.severity.name)}]</b> ${html(it.title)}<br>")
                appendLine("<small>${html(it.description)}</small></li>")
            }
            appendLine("</ul>")
        }
        appendLine("</body></html>")
    }
    private fun html(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace(34.toChar().toString(), "&quot;")
        .replace("'", "&#39;")
}
