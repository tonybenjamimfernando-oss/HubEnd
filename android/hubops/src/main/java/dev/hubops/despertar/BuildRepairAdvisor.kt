package dev.hubops.despertar

/** Diagnóstico determinístico de logs Gradle. Sugere ações; nunca altera código automaticamente. */
data class BuildRepairFinding(
    val id: String,
    val severity: Severity,
    val title: String,
    val evidence: String,
    val recommendation: String,
    val confidence: Int,
) {
    enum class Severity { ERROR, WARNING, INFO }
}

object BuildRepairAdvisor {
    private data class Rule(
        val id: String,
        val severity: BuildRepairFinding.Severity,
        val title: String,
        val pattern: Regex,
        val recommendation: String,
        val confidence: Int = 90,
    )

    private val rules = listOf(
        Rule("sdk_missing", BuildRepairFinding.Severity.ERROR, "Android SDK ou plataforma ausente",
            Regex("(?i)(SDK location not found|failed to find target with hash string|platforms;android-[0-9]+.*not found|ANDROID_HOME.*not set)"),
            "Instale a plataforma Android exigida pelo projeto e configure ANDROID_HOME ou ANDROID_SDK_ROOT. Confirme também que o Termux consegue ler o diretório do SDK."),
        Rule("jdk_version", BuildRepairFinding.Severity.ERROR, "Versão do Java incompatível",
            Regex("(?i)(invalid source release: ?(1\\.)?(8|11|17|21)|requires? java [0-9]+|unsupported class file major version|class file has wrong version)"),
            "Verifique a versão exigida pelo Android Gradle Plugin e pelo projeto. Instale essa versão do JDK e confirme java -version antes de recompilar."),
        Rule("gradle_plugin", BuildRepairFinding.Severity.ERROR, "Incompatibilidade Gradle / Android Gradle Plugin",
            Regex("(?i)(minimum supported Gradle version is|requires? Gradle version|incompatible.*android gradle plugin|plugin.*requires.*gradle)"),
            "Alinhe a versão do Gradle com a versão do Android Gradle Plugin indicada no erro. Atualize o wrapper e valide a matriz oficial de compatibilidade."),
        Rule("dependency_network", BuildRepairFinding.Severity.ERROR, "Falha ao descarregar dependências",
            Regex("(?i)(could not resolve .*|could not GET .*|unknownhostexception|connection timed out|connection reset|no cached version .* available)"),
            "Verifique a ligação à Internet, DNS, repositórios Maven/Google e proxy. Repita sem modo offline; não remova dependências apenas para ocultar o erro."),
        Rule("unresolved_symbol", BuildRepairFinding.Severity.ERROR, "Símbolo ou referência não resolvida",
            Regex("(?i)(unresolved reference:|cannot find symbol|cannot resolve symbol|undeclared identifier)"),
            "Abra o ficheiro e a linha indicados no log. Confirme imports, nome do símbolo, módulo que o declara e dependência correspondente. Evite criar stubs para silenciar o compilador."),
        Rule("duplicate_class", BuildRepairFinding.Severity.ERROR, "Classe duplicada no classpath",
            Regex("(?i)(duplicate class .* found in modules|program type already present|duplicate entry: .*\\.class)"),
            "Identifique as duas dependências que incluem a mesma classe. Alinhe versões ou exclua a dependência transitiva redundante de forma explícita."),
        Rule("manifest_merge", BuildRepairFinding.Severity.ERROR, "Conflito na fusão do AndroidManifest",
            Regex("(?i)(manifest merger failed|attribute .* is also present at|manifest merger.*error)"),
            "Consulte o relatório do Manifest Merger. Resolva o atributo no manifesto de origem e use tools:replace/tools:remove somente quando a intenção estiver confirmada."),
        Rule("out_of_memory", BuildRepairFinding.Severity.ERROR, "Memória insuficiente durante a compilação",
            Regex("(?i)(java heap space|outofmemoryerror|GC overhead limit exceeded|daemon disappeared unexpectedly)"),
            "Feche aplicações pesadas, reduza workers paralelos e ajuste org.gradle.jvmargs de acordo com a RAM disponível. Evite aumentar o heap acima da memória física."),
        Rule("disk_space", BuildRepairFinding.Severity.ERROR, "Espaço de armazenamento insuficiente",
            Regex("(?i)(no space left on device|not enough space|disk quota exceeded)"),
            "Libere espaço no armazenamento do dispositivo e no diretório ~/.gradle. Preserve os projetos e artefactos necessários antes de limpar caches."),
        Rule("kotlin_metadata", BuildRepairFinding.Severity.ERROR, "Incompatibilidade de metadados Kotlin",
            Regex("(?i)(binary version of its metadata is .* expected version|metadata version.*incompatible|compiled with an incompatible version of kotlin)"),
            "Alinhe o Kotlin Gradle Plugin com as versões Kotlin usadas pelas bibliotecas. Atualize de forma coordenada e depois execute clean e assembleDebug."),
        Rule("permission_denied", BuildRepairFinding.Severity.ERROR, "Permissão negada ao aceder a ficheiros",
            Regex("(?i)(permission denied|eacces|operation not permitted)"),
            "Confirme permissões do armazenamento Android e propriedade dos diretórios no Termux. Trabalhe numa pasta própria do Termux e exporte o APK apenas no fim."),
        Rule("compile_failed", BuildRepairFinding.Severity.ERROR, "Falha de compilação sem causa específica reconhecida",
            Regex("(?i)(> compilation failed|execution failed for task .*compile|compilation error)"),
            "Leia as primeiras mensagens de erro anteriores a BUILD FAILED. O último erro costuma ser consequência; corrija primeiro o primeiro erro concreto."),
    )

    fun analyze(stdout: String, stderr: String, exitCode: Int): List<BuildRepairFinding> {
        val combined = (stderr + "\n" + stdout).takeLast(MAX_LOG_CHARS)
        if (combined.isBlank()) {
            // HubEnd: um registo vazio com exit 0 não pode ser apresentado como build verificado.
            return if (exitCode == 0) listOf(BuildRepairFinding(
                "empty_success_log", BuildRepairFinding.Severity.INFO,
                "Tarefa terminou sem registo textual", "Exit code: 0",
                "Sem registo não há evidência do build; isto não substitui a validação do APK, da assinatura e dos testes no dispositivo.", 60,
            )) else listOf(BuildRepairFinding(
                "empty_failure_log", BuildRepairFinding.Severity.WARNING,
                "A tarefa falhou sem registo textual disponível", "Exit code: $exitCode",
                "Verifique o callback do Termux, permissões RUN_COMMAND e se stdout/stderr foram preservados.", 100,
            ))
        }
        val findings = rules.mapNotNull { rule ->
            val match = rule.pattern.find(combined) ?: return@mapNotNull null
            val evidence = combined.substring(maxOf(0, match.range.first - 100), minOf(combined.length, match.range.last + 180))
                .replace(Regex("\\s+"), " ").trim().take(260)
            BuildRepairFinding(rule.id, rule.severity, rule.title, evidence, rule.recommendation, rule.confidence)
        }.distinctBy { it.id }.take(MAX_FINDINGS).toMutableList()

        if (exitCode != 0 && findings.isEmpty()) {
            findings += BuildRepairFinding(
                "unknown_failure", BuildRepairFinding.Severity.WARNING,
                "Erro de compilação não classificado", combined.takeLast(260).replace(Regex("\\s+"), " "),
                "Guarde o log completo e investigue a primeira ocorrência de FAILED, error ou Exception. Nenhuma alteração automática foi aplicada.", 45,
            )
        }
        if (exitCode == 0 && findings.isEmpty()) {
            findings += BuildRepairFinding(
                "no_detected_error", BuildRepairFinding.Severity.INFO,
                "Nenhum erro conhecido identificado", "Exit code: 0",
                "Isto não substitui a validação do APK, da assinatura e dos testes no dispositivo.", 80,
            )
        }
        return findings
    }

    private const val MAX_LOG_CHARS = 200_000
    private const val MAX_FINDINGS = 8
}
