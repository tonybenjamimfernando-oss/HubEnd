package dev.hubops.despertar

/**
 * Regras heurísticas transparentes. Cada sinal é localizado no próprio ficheiro/linha
 * para evitar falsos positivos criados pela concatenação de ficheiros diferentes.
 * Não substitui SAST, revisão humana ou testes dinâmicos.
 */
data class StaticCodeAuditResult(val filesReviewed: Int, val findings: List<String>)

object StaticCodeAudit {
    private data class Rule(val pattern: Regex, val message: String)

    private val lineRules = listOf(
        Rule(Regex("http://(?!localhost|127\\.0\\.0\\.1)(?:[^\\s\\\"']+)", RegexOption.IGNORE_CASE),
            "Endereço HTTP sem TLS detetado; confirme se é intencional."),
        Rule(Regex("setJavaScriptEnabled\\s*\\(\\s*true\\s*\\)"),
            "WebView com JavaScript ativado: reveja origens, bridges e conteúdo não confiável."),
        Rule(Regex("Runtime\\.getRuntime\\(\\)\\.exec|ProcessBuilder\\s*\\("),
            "Execução de processos do sistema detetada; valide argumentos, permissões e origem dos comandos."),
        Rule(Regex("(?i)(?:api[_-]?key|client[_-]?secret|password|access[_-]?token)\\s*[:=]\\s*[\"'][^\"']{8,}[\"']"),
            "Possível segredo literal; confirme se não é credencial real e use armazenamento seguro."),
        Rule(Regex("android:(?:usesCleartextTraffic|allowBackup)\\s*=\\s*\"true\"", RegexOption.IGNORE_CASE),
            "Manifesto permite tráfego sem TLS ou backup; reveja necessidade e impacto nos dados."),
        Rule(Regex("android:exported\\s*=\\s*\"true\"", RegexOption.IGNORE_CASE),
            "Componente Android exportado; confirme permissões, intents e superfície de exposição."),
    )

    fun analyze(files: Map<String, String>, repositoryPaths: List<String>): StaticCodeAuditResult {
        val findings = linkedSetOf<String>()
        files.toSortedMap().forEach { (path, content) ->
            val lines = content.lineSequence().toList()
            lines.forEachIndexed { index, line ->
                lineRules.forEach { rule ->
                    if (rule.pattern.containsMatchIn(line)) {
                        findings += "$path:${index + 1} — ${rule.message}"
                    }
                }
            }
            // Regra de contexto: só procura dentro do mesmo ficheiro, nunca entre ficheiros.
            val tlsPattern = Regex("(?is)(?:X509TrustManager|HostnameVerifier).{0,500}return\\s+true")
            val tlsMatch = tlsPattern.find(content)
            if (tlsMatch != null) {
                val line = content.take(tlsMatch.range.first).count { it == '\n' } + 1
                findings += "$path:$line — Possível validação TLS permissiva; confirme certificados e nomes de host."
            }
        }

        if (files.isEmpty()) {
            findings += "Não foi possível obter ficheiros de código/configuração para revisão; os sinais estáticos não foram avaliados."
        }

        val hasTestPaths = repositoryPaths.any { path ->
            val p = path.lowercase().replace('\\', '/')
            p.contains("/test/") || p.startsWith("test/") || p.contains("/tests/") ||
                p.startsWith("tests/") || p.contains("/androidtest/") ||
                p.endsWith("test.kt") || p.endsWith("test.java") || p.endsWith("_test.go") || p.endsWith("_test.py")
        }
        if (!hasTestPaths) findings += "Não foram identificados caminhos de testes pelos padrões conhecidos; confirme manualmente a estratégia de testes."
        if (findings.isEmpty()) findings += "Nenhum dos padrões heurísticos definidos foi encontrado nos ficheiros analisados; isto não prova ausência de vulnerabilidades."
        return StaticCodeAuditResult(files.size, findings.toList())
    }
}
