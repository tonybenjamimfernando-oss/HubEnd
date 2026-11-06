package dev.hubops.despertar.project

import dev.hubops.core.code.AstProjectReport
import dev.hubops.core.code.TreeSitterAstAnalyzer

import java.io.File
import java.util.zip.ZipInputStream

/** Análise estática local e limitada de um ZIP de projeto; não executa nem extrai ficheiros no disco. */
data class ProjectInsight(
    val filesScanned: Int,
    val sourceFiles: Int,
    val testFiles: Int,
    val kotlinFiles: Int,
    val javaFiles: Int,
    val pythonFiles: Int,
    val todoCount: Int,
    val findings: List<ProjectInsightFinding>,
    val symbolIndex: ProjectSymbolIndex,
    val astReport: AstProjectReport,
) {
    fun toMarkdown(): String = buildString {
        appendLine("# HubOps · Code Intelligence")
        appendLine("- Ficheiros analisados: $filesScanned")
        appendLine("- Fontes: $sourceFiles (Kotlin: $kotlinFiles; Java: $javaFiles; Python: $pythonFiles)")
        appendLine("- Ficheiros de teste identificados: $testFiles")
        appendLine("- Marcadores TODO/FIXME: $todoCount")
        appendLine()
        appendLine(astReport.toMarkdown())
        appendLine()
        appendLine(symbolIndex.toMarkdown())
        findings.forEach { appendLine("- **${it.severity} · ${it.title}** — ${it.detail}") }
        appendLine()
        appendLine("Análise heurística local; não substitui compilação, testes ou revisão de segurança.")
    }
}

data class ProjectInsightFinding(val severity: String, val title: String, val detail: String)

object ProjectIntelligence {
    private const val MAX_FILES = 1_000
    private const val MAX_ENTRY_BYTES = 2 * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 20 * 1024 * 1024
    private val sourceExtensions = setOf("kt", "java", "py")
    private val relevantExtensions = setOf("kt", "java", "kts", "py", "xml", "gradle", "properties", "md", "yml", "yaml")

    /** Lê apenas entradas relevantes, rejeita caminhos inseguros e aplica limites contra ZIP bombs. */
    fun analyzeZip(zipFile: File): ProjectInsight = analyze(readRelevantFiles(zipFile))

    /** Leitura comum, limitada e sem extração para disco, reutilizada pelos analisadores do Despertar. */
    fun readRelevantFiles(zipFile: File): Map<String, String> {
        require(zipFile.isFile && zipFile.length() > 0L) { "ZIP de projeto inexistente ou vazio." }
        val contents = linkedMapOf<String, String>()
        var total = 0
        var entriesSeen = 0
        ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entriesSeen++
                require(entriesSeen <= 2_000) { "O ZIP contém mais de 2.000 entradas; análise interrompida por segurança." }
                val path = entry.name.replace('\\', '/')
                val safe = !entry.isDirectory && !path.startsWith("/") &&
                    path.split('/').none { it == ".." || it.isBlank() }
                val basename = path.substringAfterLast('/')
                val ext = path.substringAfterLast('.', "").lowercase()
                val relevant = safe && (ext in relevantExtensions || basename.startsWith("gradlew", true))
                val out = if (relevant && contents.size < MAX_FILES) java.io.ByteArrayOutputStream() else null
                val buffer = ByteArray(8 * 1024)
                var entryBytes = 0
                while (true) {
                    val n = zip.read(buffer)
                    if (n < 0) break
                    entryBytes += n
                    total += n
                    require(entryBytes <= MAX_ENTRY_BYTES && total <= MAX_TOTAL_BYTES) {
                        "O ZIP excede os limites de leitura segura da análise (2 MB por entrada; 20 MB total descompactado)."
                    }
                    out?.write(buffer, 0, n)
                }
                if (out != null) contents[path] = out.toString(Charsets.UTF_8.name())
                zip.closeEntry()
            }
        }
        return contents
    }

    fun analyze(files: Map<String, String>): ProjectInsight {
        val paths = files.keys.toList()
        val sourcePaths = paths.filter { it.substringAfterLast('.', "").lowercase() in sourceExtensions }
        val tests = sourcePaths.filter(::isTestPath)
        val productionSources = sourcePaths.filterNot(::isTestPath)
        val kotlin = productionSources.count { it.endsWith(".kt", true) || it.endsWith(".kts", true) }
        val java = productionSources.count { it.endsWith(".java", true) }
        val python = productionSources.count { it.endsWith(".py", true) }
        val todos = files.values.sumOf { text -> Regex("(?i)\\b(TODO|FIXME)\\b").findAll(text).count() }
        val findings = mutableListOf<ProjectInsightFinding>()
        if (files.size >= MAX_FILES) findings += ProjectInsightFinding("MÉDIO", "Limite de ficheiros atingido", "A análise guardou os primeiros $MAX_FILES ficheiros relevantes; os restantes não foram incluídos.")

        if (sourcePaths.isEmpty()) findings += ProjectInsightFinding("ALTO", "Código-fonte não localizado", "O ZIP não contém ficheiros .kt, .kts, .java ou .py nos caminhos analisados.")
        if (tests.isEmpty()) findings += ProjectInsightFinding("MÉDIO", "Testes não identificados", "Não foram encontrados caminhos ou nomes de ficheiros com padrões comuns de teste.")
        if (todos > 0) findings += ProjectInsightFinding("BAIXO", "Trabalho pendente", "$todos marcadores TODO/FIXME encontrados; reveja cada ocorrência antes da entrega.")

        sourcePaths.forEach { path ->
            val text = files[path].orEmpty()
            val lines = text.lineSequence().count()
            if (lines > 500) findings += ProjectInsightFinding("BAIXO", "Ficheiro extenso", "$path contém $lines linhas; considere separar responsabilidades se a complexidade justificar.")
            if (path.endsWith(".kt", true) && text.isNotBlank() &&
                !Regex("(?m)^\\s*package\\s+[A-Za-z_][\\w.]*").containsMatchIn(text)) {
                findings += ProjectInsightFinding("MÉDIO", "Package Kotlin não declarado", "$path não apresenta uma declaração package reconhecível; confirme se é intencional.")
            }
        }

        val hasManifest = paths.any { it.endsWith("AndroidManifest.xml", true) }
        val hasGradle = paths.any { it == "build.gradle" || it == "build.gradle.kts" || it.endsWith("/build.gradle") || it.endsWith("/build.gradle.kts") }
        val hasSettings = paths.any { it == "settings.gradle" || it == "settings.gradle.kts" || it.endsWith("/settings.gradle") || it.endsWith("/settings.gradle.kts") }
        val hasWrapperScript = paths.any { it == "gradlew" || it.endsWith("/gradlew") || it == "gradlew.bat" || it.endsWith("/gradlew.bat") }
        val hasWrapperJar = paths.any { it.endsWith("gradle/wrapper/gradle-wrapper.jar") }
        val hasWrapperProperties = paths.any { it.endsWith("gradle/wrapper/gradle-wrapper.properties") }
        val hasWrapper = hasWrapperScript && hasWrapperJar && hasWrapperProperties
        if (!hasManifest) findings += ProjectInsightFinding("MÉDIO", "Manifesto Android ausente", "Não foi localizado AndroidManifest.xml; confirme se o ZIP é um módulo Android completo.")
        if (!hasGradle || !hasSettings) findings += ProjectInsightFinding("ALTO", "Configuração Gradle incompleta", "Não foram localizados os ficheiros Gradle de raiz e/ou settings; o projeto pode não ser compilável como exportado.")
        if (!hasWrapper) findings += ProjectInsightFinding(
            "BAIXO", "Gradle Wrapper ausente ou incompleto",
            "O wrapper só é considerado funcional quando existem gradlew, gradle/wrapper/gradle-wrapper.jar e gradle-wrapper.properties. Será necessário Gradle instalado ou adicionar os três componentes oficiais.",
        )

        if (findings.isEmpty()) findings += ProjectInsightFinding("INFO", "Sem alertas heurísticos", "Os padrões básicos foram encontrados; isso não confirma compilação nem qualidade integral.")
        val symbolIndex = ProjectSymbolIndexer.index(files)
        val astReport = TreeSitterAstAnalyzer.analyze(files)
        if (astReport.filesOmitted > 0) findings += ProjectInsightFinding(
            "MÉDIO", "Limite de ficheiros AST atingido",
            "${astReport.filesOmitted} fontes não foram analisadas porque o limite AST é de 1.000 ficheiros.",
        )
        if (astReport.truncatedFiles > 0) findings += ProjectInsightFinding(
            "BAIXO", "Limite de nós AST atingido",
            "${astReport.truncatedFiles} ficheiros atingiram o limite de 100.000 nós; as árvores estão parciais.",
        )
        if (astReport.filesUnavailable > 0) findings += ProjectInsightFinding(
            "BAIXO", "AST parcialmente indisponível",
            "${astReport.filesParsed} ficheiros analisados por Tree-sitter; ${astReport.filesUnavailable} não foram analisados por falta de grammar/JNI ou por limites. Consulte o relatório AST.",
        )
        if (astReport.filesWithSyntaxErrors > 0) findings += ProjectInsightFinding(
            "ALTO", "Possíveis erros sintáticos",
            "Tree-sitter encontrou erros sintáticos em ${astReport.filesWithSyntaxErrors} ficheiros. Confirme com o compilador/interpreter oficial.",
        )
        if (symbolIndex.ambiguousNames > 0) findings += ProjectInsightFinding(
            "MÉDIO", "Nomes de símbolos ambíguos",
            "${symbolIndex.ambiguousNames} nomes têm mais de uma declaração lexical. Verifique packages, escopos e sobrecargas antes de alterar referências.",
        )
        if (symbolIndex.truncatedFiles > 0) findings += ProjectInsightFinding(
            "BAIXO", "Índice lexical limitado",
            "${symbolIndex.truncatedFiles} ficheiros atingiram limites de tamanho ou tokens; algumas declarações/referências podem não ter sido indexadas.",
        )
        return ProjectInsight(files.size, productionSources.size, tests.size, kotlin, java, python, todos,
            findings.distinctBy { it.title to it.detail }.take(40), symbolIndex, astReport)
    }

    private fun isTestPath(path: String): Boolean {
        val segments = path.lowercase().replace('\\', '/').split('/')
        val name = segments.lastOrNull().orEmpty()
        val testDirectories = setOf("test", "tests", "androidtest", "androidtests")
        return segments.dropLast(1).any { it in testDirectories } ||
            name.endsWith("test.kt") || name.endsWith("tests.kt") ||
            name.endsWith("test.java") || name.endsWith("tests.java") ||
            name.endsWith("_test.py") || name.endsWith("test.py")
    }
}
