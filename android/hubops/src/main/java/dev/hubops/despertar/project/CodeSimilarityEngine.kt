package dev.hubops.despertar.project

import kotlin.math.max
import kotlin.math.roundToInt

/** A matched pair. [path] is retained for UI compatibility and is the original-side path. */
data class SimilarityFileResult(
    val path: String,
    val language: String,
    val textualPercent: Int,
    val structuralPercent: Int,
    val originalTokens: Int,
    val replicaTokens: Int,
    val replicaPath: String = path,
    val matchType: String = "CAMINHO_EXATO",
)

data class CodeSimilarityReport(
    val originalSourceFiles: Int,
    val replicaSourceFiles: Int,
    val comparedFiles: Int,
    val unmatchedOriginal: Int,
    val unmatchedReplica: Int,
    val textualPercent: Int,
    val structuralPercent: Int,
    val files: List<SimilarityFileResult>,
    val refactoringStatus: String,
    val limitations: List<String>,
    val movedOrRenamedFiles: Int = 0,
    val matchingComparisons: Int = 0,
    val matchingSearchTruncated: Boolean = false,
    val truncatedSourceFiles: Int = 0,
    val truncatedFileCount: Int = 0,
) {
    fun toMarkdown(): String = buildString {
        appendLine("# HubOps · Code Similarity & Refactoring Lab")
        appendLine("- Fontes originais: $originalSourceFiles · fontes da réplica: $replicaSourceFiles")
        appendLine("- Ficheiros comparados: $comparedFiles · sem par: $unmatchedOriginal original / $unmatchedReplica réplica")
        appendLine("- Ficheiros movidos/renomeados emparelhados: $movedOrRenamedFiles")
        appendLine("- Similaridade textual média: $textualPercent%")
        appendLine("- Similaridade estrutural lexical média: $structuralPercent%")
        appendLine("- Comparações usadas para encontrar pares: $matchingComparisons${if (matchingSearchTruncated) " (limite atingido)" else ""}")
        appendLine("- Fontes ignoradas pelo limite de inventário: $truncatedSourceFiles · ficheiros truncados por tamanho/tokens: $truncatedFileCount")
        appendLine("- Refatoração: $refactoringStatus")
        appendLine()
        appendLine("## Por ficheiro")
        files.forEach {
            val pair = if (it.path == it.replicaPath) "`${it.path}`" else "`${it.path}` → `${it.replicaPath}` (${it.matchType})"
            appendLine("- $pair (${it.language}): texto ${it.textualPercent}% · estrutura ${it.structuralPercent}% · tokens ${it.originalTokens}/${it.replicaTokens}")
        }
        appendLine()
        appendLine("## Limitações e segurança")
        limitations.forEach { appendLine("- $it") }
        appendLine("\nAs percentagens são métricas de semelhança, não prova de autoria, equivalência funcional ou conformidade de licença.")
    }
}

/**
 * Bounded source comparison. Uses ordered token n-grams and conservative move matching.
 * This is deliberately NOT advertised as a compiler AST and never rewrites source.
 */
object CodeSimilarityEngine {
    private val extensions = mapOf("kt" to "Kotlin", "kts" to "Kotlin DSL", "java" to "Java", "py" to "Python")
    private const val MAX_SOURCE_FILES = 1_000
    private const val MAX_FILE_CHARS = 1_000_000
    private const val MAX_TOKENS_PER_FILE = 100_000
    private const val MAX_MOVE_CANDIDATE_COMPARISONS = 20_000
    private const val MOVE_MATCH_THRESHOLD = 0.58

    private data class Prepared(val path: String, val language: String, val tokens: List<String>, val canonical: List<String>, val truncated: Boolean)
    private data class Pairing(val left: Prepared, val right: Prepared, val matchType: String, val structural: Double)

    fun compare(original: Map<String, String>, replica: Map<String, String>): CodeSimilarityReport {
        val normalizedA = normalizeRoot(original)
        val normalizedB = normalizeRoot(replica)
        val allA = sourceFiles(normalizedA)
        val allB = sourceFiles(normalizedB)
        require(allA.isNotEmpty() || allB.isNotEmpty()) { "Os ZIPs não contêm fontes Python, Kotlin ou Java analisáveis." }

        val a = allA.entries.map { prepare(it.key, it.value) }
        val b = allB.entries.map { prepare(it.key, it.value) }
        val aByPath = a.associateBy { it.path }
        val bByPath = b.associateBy { it.path }
        val pairings = mutableListOf<Pairing>()
        val usedA = mutableSetOf<String>()
        val usedB = mutableSetOf<String>()

        // First pass: stable exact relative-path matches.
        (aByPath.keys intersect bByPath.keys).sorted().forEach { path ->
            val left = aByPath.getValue(path)
            val right = bByPath.getValue(path)
            pairings += Pairing(left, right, "CAMINHO_EXATO", orderedDice(left.tokens, right.tokens))
            usedA += path
            usedB += path
        }

        // Second pass: identify moved/renamed files using same-language ordered structure.
        val remainingA = a.filterNot { it.path in usedA }
        val remainingB = b.filterNot { it.path in usedB }
        var comparisons = 0
        var searchTruncated = false
        val candidates = ArrayList<Triple<Prepared, Prepared, Double>>()
        val bByLanguage = remainingB.groupBy { it.language }
        outer@ for (left in remainingA.sortedBy { it.path }) {
            val candidatesForLanguage = bByLanguage[left.language].orEmpty()
            // Same basename is prioritized but not required; all candidates are deterministic.
            val orderedCandidates = candidatesForLanguage.sortedWith(
                compareBy<Prepared> { if (basename(it.path).equals(basename(left.path), true)) 0 else 1 }.thenBy { it.path }
            )
            for (right in orderedCandidates) {
                if (comparisons >= MAX_MOVE_CANDIDATE_COMPARISONS) {
                    searchTruncated = true
                    break@outer
                }
                comparisons++
                // Very short files (e.g. "class A") have generic shapes and are unsafe to pair by guess.
                if (left.tokens.size < 8 || right.tokens.size < 8) continue
                val score = orderedDice(left.canonical, right.canonical)
                if (score >= MOVE_MATCH_THRESHOLD) candidates += Triple(left, right, score)
            }
        }
        val selected = candidates.sortedWith(
            compareByDescending<Triple<Prepared, Prepared, Double>> { it.third }
                .thenBy { it.first.path }.thenBy { it.second.path }
        )
        selected.forEach { (left, right, score) ->
            if (left.path !in usedA && right.path !in usedB) {
                pairings += Pairing(left, right, "MOVIDO_OU_RENOMEADO", score)
                usedA += left.path
                usedB += right.path
            }
        }

        val results = pairings.map { pair ->
            SimilarityFileResult(
                path = pair.left.path,
                language = pair.left.language,
                textualPercent = percent(orderedDice(pair.left.tokens, pair.right.tokens)),
                structuralPercent = percent(orderedDice(pair.left.canonical, pair.right.canonical)),
                originalTokens = pair.left.tokens.size,
                replicaTokens = pair.right.tokens.size,
                replicaPath = pair.right.path,
                matchType = pair.matchType,
            )
        }.sortedBy { it.path }

        // Missing files contribute zero by remaining in the project-level denominator.
        val denominator = max(allA.size, allB.size).coerceAtLeast(1)
        val text = (results.sumOf { it.textualPercent } / denominator).coerceIn(0, 100)
        val structure = (results.sumOf { it.structuralPercent } / denominator).coerceIn(0, 100)
        val truncatedFiles = (a + b).count { it.truncated }
        val omitted = (originalSourceCount(normalizedA) - allA.size).coerceAtLeast(0) +
            (originalSourceCount(normalizedB) - allB.size).coerceAtLeast(0)

        val limitations = mutableListOf(
            "A métrica usa trigramas ordenados de tokens; não é AST de compilador nem mede equivalência semântica.",
            "Ficheiros movidos/renomeados são emparelhados heuristicamente quando a estrutura lexical atinge pelo menos ${(MOVE_MATCH_THRESHOLD * 100).roundToInt()}%; confirme manualmente pares ambíguos.",
            "Python, Kotlin, Kotlin DSL e Java são reconhecidos lexicalmente. A refatoração AST e a resolução semântica de símbolos ainda não estão implementadas.",
            "A comparação estrutural abstrai identificadores e literais, mas preserva palavras-chave, operadores e ordem dos tokens.",
            "Ficheiros acima de 1 MB ou com mais de 100.000 tokens são truncados; consulte os contadores deste relatório.",
            "O resultado agregado penaliza ficheiros sem par e divide pelo maior inventário dos dois projetos.",
            "A procura de pares movidos está limitada a $MAX_MOVE_CANDIDATE_COMPARISONS comparações para proteger memória e latência.",
        )
        if (searchTruncated) limitations += "A procura heurística de ficheiros movidos atingiu o limite; alguns pares potenciais podem permanecer sem correspondência."
        if (omitted > 0) limitations += "$omitted ficheiros-fonte foram omitidos porque o inventário excedeu $MAX_SOURCE_FILES por projeto."
        if (truncatedFiles > 0) limitations += "$truncatedFiles ficheiros tiveram conteúdo truncado pelos limites de tamanho ou tokens."

        return CodeSimilarityReport(
            originalSourceFiles = allA.size,
            replicaSourceFiles = allB.size,
            comparedFiles = results.size,
            unmatchedOriginal = allA.size - usedA.size,
            unmatchedReplica = allB.size - usedB.size,
            textualPercent = text,
            structuralPercent = structure,
            files = results,
            refactoringStatus = "NÃO IMPLEMENTADA — análise lexical; nenhuma renomeação ou refatoração foi aplicada",
            limitations = limitations,
            movedOrRenamedFiles = results.count { it.matchType == "MOVIDO_OU_RENOMEADO" },
            matchingComparisons = comparisons,
            matchingSearchTruncated = searchTruncated,
            truncatedSourceFiles = omitted,
            truncatedFileCount = truncatedFiles,
        )
    }

    private fun prepare(path: String, source: String): Prepared {
        val language = extensions[path.substringAfterLast('.', "").lowercase()] ?: "Desconhecida"
        val limited = source.take(MAX_FILE_CHARS)
        val raw = tokenize(limited, language).take(MAX_TOKENS_PER_FILE)
        return Prepared(path, language, raw, canonicalize(raw), source.length > MAX_FILE_CHARS || raw.size >= MAX_TOKENS_PER_FILE)
    }

    private fun normalizeRoot(files: Map<String, String>): Map<String, String> {
        // Preserve every meaningful path segment. Guessing and stripping a top-level
        // directory can erase legitimate module/source roots such as "core" or "app".
        val normalized = files.entries.mapNotNull { (path, value) ->
            val clean = path.replace('\\', '/').trimStart('/')
            if (clean.isBlank() || clean.split('/').any { it == ".." || it.isBlank() }) null else clean to value
        }
        require(normalized.map { it.first }.distinct().size == normalized.size) {
            "O ZIP contém caminhos duplicados após normalização (por exemplo, barras diferentes); comparação interrompida para evitar associar o ficheiro errado."
        }
        return normalized.toMap()
    }

    private fun sourceFiles(files: Map<String, String>): Map<String, String> = files.entries.asSequence()
        .filter { extensions.containsKey(it.key.substringAfterLast('.', "").lowercase()) }
        .filterNot { isTest(it.key) }
        .sortedBy { it.key }
        .take(MAX_SOURCE_FILES)
        .associate { it.key to it.value }

    private fun originalSourceCount(files: Map<String, String>): Int = files.keys.count {
        extensions.containsKey(it.substringAfterLast('.', "").lowercase()) && !isTest(it)
    }

    private fun isTest(path: String): Boolean {
        val segments = path.lowercase().replace('\\', '/').split('/')
        val name = segments.lastOrNull().orEmpty()
        val dirs = setOf("test", "tests", "androidtest", "androidtests")
        return segments.dropLast(1).any { it in dirs } ||
            name.endsWith("test.kt") || name.endsWith("test.kts") || name.endsWith("test.java") ||
            name.endsWith("_test.py") || name.endsWith("test.py")
    }

    /** Small bounded lexer: comments are removed; quoted literals and operators are retained. */
    private fun tokenize(source: String, language: String): List<String> {
        val out = ArrayList<String>(minOf(source.length / 3, 16_384))
        var i = 0
        while (i < source.length && out.size < MAX_TOKENS_PER_FILE) {
            val c = source[i]
            if (c.isWhitespace()) { i++; continue }
            if (language == "Python" && c == '#') {
                while (i < source.length && source[i] != '\n') i++
                continue
            }
            if (language != "Python" && c == '/' && i + 1 < source.length && source[i + 1] == '/') {
                i += 2; while (i < source.length && source[i] != '\n') i++; continue
            }
            if (c == '/' && i + 1 < source.length && source[i + 1] == '*') {
                i += 2
                var depth = 1
                val nested = language == "Kotlin" || language == "Kotlin DSL"
                while (i < source.length && depth > 0) {
                    if (nested && i + 1 < source.length && source[i] == '/' && source[i + 1] == '*') { depth++; i += 2 }
                    else if (i + 1 < source.length && source[i] == '*' && source[i + 1] == '/') { depth--; i += 2 }
                    else i++
                }
                continue
            }
            if (c == '"' || c == '\'') {
                val start = i
                val triple = i + 2 < source.length && source[i + 1] == c && source[i + 2] == c &&
                    (language == "Python" || c == '"')
                val delimiter = if (triple) 3 else 1
                i += delimiter
                while (i < source.length) {
                    if (!triple && source[i] == '\\') { i = (i + 2).coerceAtMost(source.length); continue }
                    val closes = i + delimiter <= source.length && (0 until delimiter).all { source[i + it] == c }
                    if (closes) { i += delimiter; break }
                    i++
                }
                out += source.substring(start, i)
                continue
            }
            if (c.isLetter() || c == '_' || c == '$') {
                val start = i++
                while (i < source.length && (source[i].isLetterOrDigit() || source[i] == '_' || source[i] == '$')) i++
                out += source.substring(start, i); continue
            }
            if (c.isDigit()) {
                val start = i++
                while (i < source.length && (source[i].isLetterOrDigit() || source[i] == '.' || source[i] == '_' || source[i] == '+') ) {
                    if ((source[i] == '+' || source[i] == '-') && source[i - 1] !in "eEpP") break
                    i++
                }
                out += source.substring(start, i); continue
            }
            val operators = listOf(">>>=", "===", "!==", "...", "::", "->", "=>", "==", "!=", "<=", ">=", "&&", "||", "++", "--", "+=", "-=", "*=", "/=", "%=", "?.", "?:", "!!", "**", "//", "<<", ">>", "..")
            val op = operators.firstOrNull { it.isNotBlank() && source.startsWith(it.trimEnd(), i) }
            if (op != null) { val actual = op.trimEnd(); out += actual; i += actual.length } else { out += c.toString(); i++ }
        }
        return out
    }

    private fun canonicalize(tokens: List<String>): List<String> = tokens.map { token ->
        when {
            token.startsWith("\"") || token.startsWith("'") -> "<STRING>"
            token.firstOrNull()?.isDigit() == true -> "<NUMBER>"
            token.firstOrNull()?.let { it.isLetter() || it == '_' || it == '$' } == true && token !in keywords -> "<ID>"
            else -> token
        }
    }

    private val keywords = setOf(
        "package", "import", "class", "interface", "object", "fun", "val", "var", "const", "return", "if", "else", "when", "for", "while", "do", "try", "catch", "finally", "throw", "new", "this", "super", "static", "final", "public", "private", "protected", "abstract", "override", "open", "internal", "suspend", "data", "enum", "sealed", "annotation", "void", "int", "long", "short", "byte", "float", "double", "boolean", "char", "String", "def", "async", "await", "yield", "lambda", "with", "as", "pass", "break", "continue", "elif", "except", "raise", "from", "None", "True", "False", "and", "or", "not", "is", "in", "assert", "del", "global", "nonlocal", "match", "case", "extends", "implements", "throws", "instanceof", "record", "vararg", "by", "companion", "typealias", "where", "get", "set", "init", "constructor", "fun", "def", "None", "True", "False"
    )

    /** Ordered trigram Dice captures token order while remaining linear and bounded. */
    private fun orderedDice(left: List<String>, right: List<String>): Double {
        if (left.isEmpty() && right.isEmpty()) return 1.0
        if (left.isEmpty() || right.isEmpty()) return 0.0
        val a = ngrams(left)
        val b = ngrams(right)
        val counts = HashMap<String, Int>(max(16, a.size * 2))
        a.forEach { counts[it] = (counts[it] ?: 0) + 1 }
        var intersection = 0
        b.forEach { token ->
            val count = counts[token] ?: 0
            if (count > 0) { intersection++; if (count == 1) counts.remove(token) else counts[token] = count - 1 }
        }
        return (2.0 * intersection) / (a.size + b.size)
    }

    private fun ngrams(tokens: List<String>): List<String> = if (tokens.size < 3) tokens else
        tokens.windowed(3).map { window -> "${window[0]}\u0001${window[1]}\u0001${window[2]}" }

    private fun basename(path: String): String = path.substringAfterLast('/')
    private fun percent(value: Double): Int = (value * 100.0).toInt().coerceIn(0, 100)
}
