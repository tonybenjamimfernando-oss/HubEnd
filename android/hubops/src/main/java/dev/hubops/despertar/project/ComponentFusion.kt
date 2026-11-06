package dev.hubops.despertar.project

import java.io.File

/** Comparação estática e somente leitura entre dois projetos Android. Não funde nem altera ficheiros. */
data class FusionReport(
    val primaryFiles: Int,
    val referenceFiles: Int,
    val exactPathConflicts: List<String>,
    val sourceNameConflicts: List<String>,
    val packageOverlaps: List<String>,
    val dependencyConflicts: List<String>,
    val permissionDifferences: List<String>,
    val findings: List<ProjectInsightFinding>,
    val integrationPlan: List<String>,
) {
    fun toMarkdown(): String = buildString {
        appendLine("# HubOps · Component Fusion")
        appendLine("Comparação somente leitura; nenhum código foi copiado ou modificado.")
        appendLine("- Ficheiros analisados: projeto principal $primaryFiles · projeto de referência $referenceFiles")
        appendLine()
        section("Conflitos de caminho", exactPathConflicts)
        section("Nomes de fontes repetidos", sourceNameConflicts)
        section("Packages partilhados", packageOverlaps)
        section("Possíveis conflitos de dependências", dependencyConflicts)
        section("Diferenças de permissões", permissionDifferences)
        appendLine("## Observações")
        findings.forEach { appendLine("- **${it.severity} · ${it.title}** — ${it.detail}") }
        appendLine("\n## Plano de integração sugerido")
        integrationPlan.forEachIndexed { i, step -> appendLine("${i + 1}. $step") }
        appendLine("\nA análise é heurística. Reveja licenças, APIs, recursos, manifestos e testes antes de qualquer fusão.")
    }

    private fun StringBuilder.section(title: String, values: List<String>) {
        appendLine("## $title")
        if (values.isEmpty()) appendLine("- Nenhum caso identificado.")
        else values.forEach { appendLine("- $it") }
        appendLine()
    }
}

object ComponentFusion {
    private val sourceExt = setOf("kt", "java")
    private val packageRegex = Regex("(?m)^\\s*package\\s+([A-Za-z_][\\w.]*)")
    private val dependencyRegex = Regex("\\b(?:androidTestImplementation|testImplementation|implementation|api|kapt)\\s*\\(?\\s*[\"\']([^\"\']+)[\"\']")
    private val permissionRegex = Regex("<uses-permission[^>]*android:name\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)

    fun analyzeZip(primaryZip: File, referenceZip: File): FusionReport = analyze(
        ProjectIntelligence.readRelevantFiles(primaryZip),
        ProjectIntelligence.readRelevantFiles(referenceZip),
    )

    fun analyze(primary: Map<String, String>, reference: Map<String, String>): FusionReport {
        require(primary.isNotEmpty()) { "O projeto principal não contém ficheiros analisáveis." }
        require(reference.isNotEmpty()) { "O projeto de referência não contém ficheiros analisáveis." }
        val a = normalize(primary)
        val b = normalize(reference)
        val sourceA = a.filterKeys { it.substringAfterLast('.', "").lowercase() in sourceExt }
        val sourceB = b.filterKeys { it.substringAfterLast('.', "").lowercase() in sourceExt }
        val exact = (sourceA.keys intersect sourceB.keys).sorted()
        val namesA = sourceA.keys.groupBy { it.substringAfterLast('/') }
        val namesB = sourceB.keys.groupBy { it.substringAfterLast('/') }
        val nameConflicts = (namesA.keys intersect namesB.keys).filter { name ->
            namesA[name].orEmpty().any { left -> namesB[name].orEmpty().any { it != left } }
        }.sorted()
        val packagesA = sourceA.values.flatMap { packageRegex.findAll(it).map { m -> m.groupValues[1] }.toList() }.toSet()
        val packagesB = sourceB.values.flatMap { packageRegex.findAll(it).map { m -> m.groupValues[1] }.toList() }.toSet()
        val overlaps = (packagesA intersect packagesB).sorted()
        val depsA = dependencies(a)
        val depsB = dependencies(b)
        val versionsA = depsA.associateBy { it.substringBeforeLast(':', it) }
        val versionsB = depsB.associateBy { it.substringBeforeLast(':', it) }
        val depConflicts = (versionsA.keys intersect versionsB.keys).mapNotNull { key ->
            val left = versionsA[key] ?: return@mapNotNull null
            val right = versionsB[key] ?: return@mapNotNull null
            if (left != right) "$key: $left vs $right" else null
        }.sorted()
        val permissionsA = permissions(a)
        val permissionsB = permissions(b)
        val permissionDiff = buildList {
            (permissionsB - permissionsA).sorted().forEach { add("Só no projeto de referência: $it") }
            (permissionsA - permissionsB).sorted().forEach { add("Só no projeto principal: $it") }
        }
        val findings = mutableListOf<ProjectInsightFinding>()
        if (exact.isNotEmpty()) findings += ProjectInsightFinding("ALTO", "Colisão de caminhos", "${exact.size} caminhos de ficheiros-fonte coincidem; não substitua ficheiros sem revisão linha a linha.")
        if (nameConflicts.isNotEmpty()) findings += ProjectInsightFinding("MÉDIO", "Nomes de fontes repetidos", "${nameConflicts.size} nomes de ficheiro aparecem em ambos os projetos, embora em caminhos diferentes.")
        if (overlaps.isNotEmpty()) findings += ProjectInsightFinding("MÉDIO", "Packages partilhados", "${overlaps.size} packages aparecem nos dois projetos; verifique classes, nomes e responsabilidades.")
        if (depConflicts.isNotEmpty()) findings += ProjectInsightFinding("ALTO", "Versões de dependências divergentes", "${depConflicts.size} coordenadas parecem usar versões diferentes.")
        if (findings.isEmpty()) findings += ProjectInsightFinding("INFO", "Sem colisões básicas identificadas", "Não foram detetados os conflitos monitorizados; isso não prova compatibilidade.")
        val plan = buildList {
            add("Confirmar a licença e a autorização de reutilização do projeto de referência.")
            if (exact.isNotEmpty() || nameConflicts.isNotEmpty()) add("Resolver colisões de ficheiros e nomes; comparar APIs e preservar o comportamento existente.")
            if (overlaps.isNotEmpty()) add("Rever os packages partilhados e decidir explicitamente entre reutilizar, adaptar ou renomear componentes.")
            if (depConflicts.isNotEmpty()) add("Uniformizar versões Gradle e validar compatibilidade com o Android Gradle Plugin e Kotlin usados no projeto principal.")
            if (permissionDiff.isNotEmpty()) add("Rever cada diferença de permissão no Manifesto; adicionar apenas permissões justificadas pela funcionalidade integrada.")
            add("Integrar um componente de cada vez, com alterações pequenas e reversíveis.")
            add("Adicionar ou adaptar testes unitários e de integração para cada componente incorporado.")
            add("Executar build, testes e inspeção do APK; comparar resultados com a versão anterior.")
        }
        return FusionReport(a.size, b.size, exact, nameConflicts, overlaps, depConflicts, permissionDiff, findings, plan)
    }

    private val conventionalRoots = setOf("src", "app", "java", "kotlin", "res", "lib", "libs")

    private fun normalize(files: Map<String, String>): Map<String, String> {
        val paths = files.keys.map { it.replace('\\', '/').trimStart('/') }
        val roots = paths.map { it.substringBefore('/') }.distinct()
        // HubEnd: só remove a pasta envolvente de um ZIP (ex.: "projeto-main/"), nunca uma pasta de código
        // convencional como "src/", que faz parte do caminho real a comparar.
        val stripRoot = roots.size == 1 && paths.all { it.contains('/') } && roots.single() !in conventionalRoots
        return files.entries.associate { (path, content) ->
            val clean = path.replace('\\', '/').trimStart('/')
            (if (stripRoot) clean.substringAfter('/') else clean) to content
        }
    }

    private fun dependencies(files: Map<String, String>): Set<String> = files
        .filterKeys { it.endsWith(".gradle") || it.endsWith(".gradle.kts") }
        .values.flatMap { text -> dependencyRegex.findAll(text).map { it.groupValues[1] }.toList() }.toSet()

    private fun permissions(files: Map<String, String>): Set<String> = files
        .filterKeys { it.endsWith("AndroidManifest.xml", true) }
        .values.flatMap { text -> permissionRegex.findAll(text).map { it.groupValues[1] }.toList() }.toSet()
}
