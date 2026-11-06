package dev.hubops.despertar

enum class LicenseClass(val label: String) {
    PERMISSIVE("Permissiva"),
    WEAK_COPYLEFT("Copyleft fraco"),
    STRONG_COPYLEFT("Copyleft forte"),
    UNKNOWN("Sem licença / desconhecida"),
}

data class RepositoryStudy(
    val fullName: String,
    val description: String,
    val url: String,
    val defaultBranch: String,
    val language: String,
    val license: String,
    val licenseClass: LicenseClass,
    val stars: Int,
    val forks: Int,
    val openIssues: Int,
    val archived: Boolean,
    val updatedAt: String,
    val topics: List<String>,
    val languages: Map<String, Long>,
    val readmeExcerpt: String,
    val rootFiles: List<String> = emptyList(),
    val buildSystems: List<String> = emptyList(),
    val dependencyManifests: List<String> = emptyList(),
    val filesReviewed: Int = 0,
    val codeFindings: List<String> = emptyList(),
    val recentCommits: List<String> = emptyList(),
    val latestRelease: String = "",
    val sourceFileCount: Int = 0,
    val testFileCount: Int = 0,
    val workflowCount: Int = 0,
    val securityPolicyFound: Boolean = false,
    val treeAvailable: Boolean = true,
    val treeTruncated: Boolean = false,
) {
    fun report(): String = buildString {
        appendLine("# Relatório de estudo — $fullName")
        appendLine()
        appendLine("- URL: $url")
        appendLine("- Descrição: ${description.ifBlank { "Não informada" }}")
        appendLine("- Ramo principal: $defaultBranch")
        appendLine("- Linguagem principal: ${language.ifBlank { "Não identificada" }}")
        appendLine("- Licença: ${license.ifBlank { "Não identificada" }} (${licenseClass.label})")
        appendLine("- Estrelas: $stars | Forks: $forks | Issues abertas: $openIssues")
        appendLine("- Arquivado: ${if (archived) "Sim" else "Não"} | Atualizado: $updatedAt")
        appendLine("- Tópicos: ${topics.joinToString().ifBlank { "Nenhum" }}")
        appendLine("- Linguagens: ${languages.entries.sortedByDescending { it.value }.joinToString { "${it.key} (${it.value} bytes)" }.ifBlank { "Não disponíveis" }}")
        appendLine()
        appendLine("## Auditoria estrutural")
        appendLine("- Ficheiros de código identificados: $sourceFileCount")
        appendLine("- Ficheiros de teste identificados: $testFileCount")
        appendLine("- Workflows GitHub Actions: $workflowCount")
        appendLine("- Política SECURITY.md: ${if (!treeAvailable) "Não avaliada" else if (securityPolicyFound) "Encontrada" else "Não encontrada na localização verificada"}")
        appendLine("- Árvore do repositório disponível: ${if (treeAvailable) "Sim" else "Não"}")
        appendLine("- Árvore do repositório truncada pela API: ${if (treeTruncated) "Sim" else "Não"}")
        appendLine("- Sistemas de build detetados: ${buildSystems.joinToString().ifBlank { "Não identificados" }}")
        appendLine("- Manifestos de dependências: ${dependencyManifests.joinToString().ifBlank { "Não identificados" }}")
        appendLine("- Ficheiros de código/configuração revistos: $filesReviewed")
        codeFindings.forEach { appendLine("- Indício: $it") }
        appendLine("- Ficheiros principais: ${rootFiles.joinToString().ifBlank { "Não disponíveis" }}")
        appendLine()
        appendLine("## Atividade recente")
        recentCommits.forEach { appendLine("- $it") }
        appendLine("- Última release: ${latestRelease.ifBlank { "Nenhuma release publicada/encontrada" }}")
        appendLine()
        appendLine("## Excerto do README")
        appendLine(readmeExcerpt.ifBlank { "README não disponível." })
        appendLine()
        appendLine("## Nota de utilização")
        appendLine("Este relatório descreve metadados públicos. Não constitui auditoria de segurança nem autorização para copiar código.")
    }
}

data class SimilarRepo(
    val fullName: String,
    val url: String,
    val spdx: String,
    val licenseClass: LicenseClass,
    val stars: Int,
)

/** Regras de licença do Despertar: estudo de ideias é sempre permitido; cópia literal só se permissiva. */
object LicensePolicy {

    fun normalize(spdx: String?): String =
        (spdx ?: "").trim().uppercase()
            .removeSuffix("-ONLY").removeSuffix("-OR-LATER").removeSuffix("+")

    fun classify(spdx: String?): LicenseClass = when (normalize(spdx)) {
        "MIT", "APACHE-2.0", "BSD-2-CLAUSE", "BSD-3-CLAUSE", "ISC", "UNLICENSE", "0BSD", "CC0-1.0" ->
            LicenseClass.PERMISSIVE
        "LGPL-2.1", "LGPL-3.0", "MPL-1.1", "MPL-2.0", "EPL-1.0", "EPL-2.0", "CDDL-1.0", "CDDL-1.1" -> LicenseClass.WEAK_COPYLEFT
        "GPL-1.0", "GPL-2.0", "GPL-3.0", "AGPL-1.0", "AGPL-3.0" -> LicenseClass.STRONG_COPYLEFT
        else -> LicenseClass.UNKNOWN
    }

    /** Reutilizar código literal exige licença permissiva identificada e atribuição. */
    fun mayCopyCode(c: LicenseClass): Boolean = c == LicenseClass.PERMISSIVE

    fun guidance(c: LicenseClass): String = when (c) {
        LicenseClass.PERMISSIVE -> "Pode reutilizar código mantendo aviso de copyright e licença."
        LicenseClass.WEAK_COPYLEFT -> "Só estude ideias; reutilizar ficheiros obriga a manter a licença original."
        LicenseClass.STRONG_COPYLEFT -> "Só estude ideias; copiar código obriga o seu projeto a adotar a mesma licença."
        LicenseClass.UNKNOWN -> "Sem licença clara = todos os direitos reservados. Só estude ideias."
    }
}

/** Gera o ATTRIBUTIONS.md que será proposto no repositório do utilizador. */
object AttributionLedger {

    fun render(project: String, entries: List<SimilarRepo>, date: String): String = buildString {
        appendLine("# Atribuições")
        appendLine()
        appendLine("Projeto: $project")
        appendLine("Gerado em: $date (HubOps · Despertar)")
        appendLine()
        appendLine("Estes repositórios públicos foram **estudados como inspiração**. Nenhum código foi copiado")
        appendLine("automaticamente. Se reutilizar código, respeite a licença indicada.")
        appendLine()
        appendLine("| Repositório | Licença | Classe | Estrelas |")
        appendLine("|---|---|---|---|")
        entries.forEach {
            val lic = it.spdx.ifBlank { "n/d" }
            appendLine("| [${it.fullName}](${it.url}) | $lic | ${it.licenseClass.label} | ${it.stars} |")
        }
    }
}
