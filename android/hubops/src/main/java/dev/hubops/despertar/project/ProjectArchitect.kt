package dev.hubops.despertar.project

import java.io.File

/** Plano arquitetural heurístico, somente leitura. Não altera fontes nem configurações. */
data class ArchitectureReport(
    val moduleNames: List<String>,
    val packageCount: Int,
    val sourceCount: Int,
    val internalImportCount: Int,
    val externalImportCount: Int,
    val packageDependencies: List<String>,
    val findings: List<ProjectInsightFinding>,
    val implementationPlan: List<String>,
) {
    fun toMarkdown(): String = buildString {
        appendLine("# HubOps · Project Architect")
        appendLine("- Módulos Gradle: ${moduleNames.joinToString().ifBlank { "não identificados" }}")
        appendLine("- Packages: $packageCount · fontes: $sourceCount")
        appendLine("- Imports internos: $internalImportCount · externos: $externalImportCount")
        appendLine()
        appendLine("## Dependências entre packages")
        if (packageDependencies.isEmpty()) appendLine("- Não foi possível inferir dependências internas.")
        else packageDependencies.forEach { appendLine("- $it") }
        appendLine()
        appendLine("## Observações")
        findings.forEach { appendLine("- **${it.severity} · ${it.title}** — ${it.detail}") }
        appendLine()
        appendLine("## Plano recomendado")
        implementationPlan.forEachIndexed { index, step -> appendLine("${index + 1}. $step") }
        appendLine()
        appendLine("Análise estática heurística; não equivale a AST completa nem validação de compilação.")
    }
}

object ProjectArchitect {
    private val sourceExt = setOf("kt", "java")
    private val packageRegex = Regex("(?m)^\\s*package\\s+([A-Za-z_][\\w.]*)")
    private val importRegex = Regex("(?m)^\\s*import\\s+(?:static\\s+)?([A-Za-z_][\\w.*]+)")
    private val moduleRegex = Regex("include\\s*\\(([^)]*)\\)")
    private val moduleTokenRegex = Regex("[\"'](:[A-Za-z0-9_:-]+)[\"']")

    fun analyzeZip(zipFile: File): ArchitectureReport = analyze(ProjectIntelligence.readRelevantFiles(zipFile))

    fun analyze(files: Map<String, String>): ArchitectureReport {
        val sources = files.filterKeys { it.substringAfterLast('.', "").lowercase() in sourceExt }
        val packageByPath = sources.mapNotNull { (path, content) ->
            packageRegex.find(content)?.groupValues?.get(1)?.let { path to it }
        }.toMap()
        val packages = packageByPath.values.toSortedSet()
        val allImports = sources.values.flatMap { content -> importRegex.findAll(content).map { it.groupValues[1].removeSuffix(".*") }.toList() }
        val internalImports = allImports.filter { imported -> packages.any { imported == it || imported.startsWith("$it.") || it.startsWith("$imported.") } }
        val externalImports = allImports.size - internalImports.size
        val dependencyPairs = linkedSetOf<String>()
        sources.forEach { (path, content) ->
            val from = packageByPath[path] ?: return@forEach
            importRegex.findAll(content).forEach { match ->
                val imported = match.groupValues[1].removeSuffix(".*")
                val target = packages.filter { imported == it || imported.startsWith("$it.") || it.startsWith("$imported.") }
                    .maxByOrNull { it.length }
                if (target != null && target != from) dependencyPairs += "$from → $target"
            }
        }
        val settings = files.filterKeys { it.substringAfterLast('/') in setOf("settings.gradle", "settings.gradle.kts") }.values.joinToString("\n")
        val modules = moduleRegex.findAll(settings).flatMap { moduleTokenRegex.findAll(it.groupValues[1]).map { token -> token.groupValues[1].removePrefix(":") } }
            .distinct().toList()
        val findings = mutableListOf<ProjectInsightFinding>()
        if (sources.isEmpty()) findings += ProjectInsightFinding("ALTO", "Sem fontes para arquitetar", "Não foram encontrados ficheiros Kotlin ou Java.")
        if (packages.isEmpty() && sources.isNotEmpty()) findings += ProjectInsightFinding("MÉDIO", "Packages não identificados", "Não foi possível inferir a organização por packages; confirme declarações package e fontes no default package.")
        val unclassified = sources.keys.filterNot { it in packageByPath }
        if (unclassified.isNotEmpty()) findings += ProjectInsightFinding("BAIXO", "Fontes sem package reconhecido", "${unclassified.size} ficheiros não apresentam uma declaração package reconhecível.")
        val fanOut = dependencyPairs.groupingBy { it.substringBefore(" → ") }.eachCount()
        fanOut.filterValues { it >= 5 }.forEach { (pkg, count) ->
            findings += ProjectInsightFinding("MÉDIO", "Muitas dependências de saída", "$pkg aponta para $count packages; avalie separar responsabilidades e reduzir acoplamento.")
        }
        val cycles = findTwoWayCycles(dependencyPairs)
        cycles.forEach { findings += ProjectInsightFinding("MÉDIO", "Dependência circular provável", "$it; considere introduzir uma camada de contratos para quebrar o ciclo.") }
        if (modules.isEmpty()) findings += ProjectInsightFinding("BAIXO", "Módulos Gradle não identificados", "Não foram encontradas declarações include(...) em settings.gradle(.kts).")
        if (findings.isEmpty()) findings += ProjectInsightFinding("INFO", "Estrutura sem alertas arquiteturais básicos", "A organização inferida não revelou os padrões heurísticos monitorizados; valide as dependências com ferramentas de build.")
        val plan = buildList {
            add("Confirmar módulos Gradle, applicationId e limites entre app, domínio, dados e interface.")
            if (packages.isNotEmpty()) add("Rever os ${packages.size} packages identificados e definir responsabilidades explícitas para cada camada.")
            if (cycles.isNotEmpty()) add("Eliminar primeiro as dependências circulares prováveis antes de adicionar novas funcionalidades.")
            if (fanOut.values.any { it >= 5 }) add("Reduzir o acoplamento dos packages com maior número de dependências de saída.")
            add("Definir interfaces para acesso a dados e serviços externos; manter regras de negócio independentes da UI.")
            add("Adicionar testes unitários para regras de negócio e testes de integração para persistência e serviços.")
            add("Só depois da revisão humana, implementar alterações em pequenos passos e compilar após cada etapa.")
        }
        return ArchitectureReport(modules, packages.size, sources.size, internalImports.size, externalImports, dependencyPairs.sorted(), findings.distinctBy { it.title to it.detail }.take(40), plan)
    }

    private fun findTwoWayCycles(edges: Set<String>): Set<String> {
        val pairs = edges.mapNotNull { edge ->
            val parts = edge.split(" → ")
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toSet()
        return pairs.mapNotNull { (a, b) -> if (a < b && (b to a) in pairs) "$a ↔ $b" else null }.toSet()
    }
}
