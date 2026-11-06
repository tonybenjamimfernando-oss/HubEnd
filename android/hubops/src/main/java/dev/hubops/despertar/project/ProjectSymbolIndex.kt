package dev.hubops.despertar.project

/**
 * Índice lexical conservador de declarações e ocorrências entre ficheiros.
 * Não é AST nem resolução semântica: resultados servem para navegação/revisão, nunca
 * autorizam renomeação automática.
 */
data class ProjectSymbol(
    val name: String,
    val kinds: List<String>,
    val declarationPaths: List<String>,
    val declarationLines: List<Int>,
    val declarationLocations: List<String>,
    val referenceCount: Int,
    val referencePaths: List<String>,
    val ambiguous: Boolean,
)

data class ProjectSymbolIndex(
    val filesIndexed: Int,
    val declarationsFound: Int,
    val referenceOccurrences: Int,
    val ambiguousNames: Int,
    val symbols: List<ProjectSymbol>,
    val truncatedFiles: Int,
) {
    fun toMarkdown(): String = buildString {
        appendLine("## Índice lexical de símbolos")
        appendLine("- Ficheiros indexados: $filesIndexed · declarações: $declarationsFound · ocorrências: $referenceOccurrences")
        appendLine("- Nomes ambíguos: $ambiguousNames · ficheiros limitados: $truncatedFiles")
        symbols.forEach { symbol ->
            appendLine("- `${symbol.name}` (${symbol.kinds.joinToString()}) — declarações: ${symbol.declarationLocations.joinToString()}; ocorrências: ${symbol.referenceCount} em ${symbol.referencePaths.joinToString().ifBlank { "nenhum outro ficheiro" }}${if (symbol.ambiguous) "; AMBÍGUO" else ""}")
        }
        appendLine("- Índice lexical, não AST: não prova ligação semântica entre ocorrências e declarações e não executa renomeações.")
    }
}

object ProjectSymbolIndexer {
    private const val MAX_FILES = 1_000
    private const val MAX_CHARS_PER_FILE = 1_000_000
    private const val MAX_TOKENS_PER_FILE = 100_000
    private const val MAX_OUTPUT_SYMBOLS = 100

    private data class Token(val text: String, val line: Int)
    private data class Declaration(val name: String, val kind: String, val path: String, val line: Int)

    private val declarationKeywords = mapOf(
        "class" to "tipo", "interface" to "interface", "object" to "object",
        "record" to "record", "fun" to "função", "def" to "função", "typealias" to "alias",
    )
    private val ignored = setOf(
        "package", "import", "class", "interface", "object", "record", "fun", "def", "typealias",
        "public", "private", "protected", "internal", "static", "final", "abstract", "open",
        "override", "suspend", "data", "enum", "sealed", "annotation", "return", "if", "else",
        "when", "for", "while", "do", "try", "catch", "finally", "throw", "new", "this", "super",
        "val", "var", "const", "get", "set", "init", "constructor", "extends", "implements", "throws",
        "void", "int", "long", "short", "byte", "float", "double", "boolean", "char", "String",
        "async", "await", "yield", "with", "as", "pass", "break", "continue", "elif", "except", "raise",
        "from", "None", "True", "False", "and", "or", "not", "is", "in", "assert", "del", "global",
        "nonlocal", "match", "case", "lambda", "self", "cls",
    )

    fun index(files: Map<String, String>): ProjectSymbolIndex {
        val sourceFiles = files.entries.asSequence()
            .filter { it.key.substringAfterLast('.', "").lowercase() in setOf("kt", "kts", "java", "py") }
            .sortedBy { it.key }
            .take(MAX_FILES)
            .toList()
        val declarations = mutableListOf<Declaration>()
        val occurrences = linkedMapOf<String, MutableList<Pair<String, Int>>>()
        var truncated = 0

        sourceFiles.forEach { (path, raw) ->
            val source = raw.take(MAX_CHARS_PER_FILE)
            if (raw.length > MAX_CHARS_PER_FILE) truncated++
            val tokens = tokenize(source)
            if (tokens.size >= MAX_TOKENS_PER_FILE) truncated++
            val declaredAt = mutableSetOf<Int>()
            tokens.forEachIndexed { index, token ->
                val kind = declarationKeywords[token.text] ?: return@forEachIndexed
                val next = tokens.getOrNull(index + 1) ?: return@forEachIndexed
                if (!isIdentifier(next.text) || next.text in ignored) return@forEachIndexed
                // `object : Interface` and malformed/incomplete declarations are ignored.
                declarations += Declaration(next.text, kind, path, next.line)
                declaredAt += index + 1
            }
            tokens.forEachIndexed { index, token ->
                if (index in declaredAt || !isIdentifier(token.text) || token.text in ignored) return@forEachIndexed
                occurrences.getOrPut(token.text) { mutableListOf() } += path to token.line
            }
        }

        val byName = declarations.groupBy { it.name }
        val symbols = byName.map { (name, defs) ->
            val refs = occurrences[name].orEmpty()
            ProjectSymbol(
                name = name,
                kinds = defs.map { it.kind }.distinct().sorted(),
                declarationPaths = defs.map { it.path }.distinct().sorted(),
                declarationLines = defs.map { it.line }.distinct().sorted(),
                declarationLocations = defs.map { "${it.path}:${it.line}" }.distinct().sorted(),
                referenceCount = refs.size,
                referencePaths = refs.map { it.first }.distinct().sorted(),
                ambiguous = defs.size > 1,
            )
        }.sortedWith(compareByDescending<ProjectSymbol> { it.ambiguous }.thenByDescending { it.referenceCount }.thenBy { it.name })

        return ProjectSymbolIndex(
            filesIndexed = sourceFiles.size,
            declarationsFound = declarations.size,
            referenceOccurrences = symbols.sumOf { it.referenceCount },
            ambiguousNames = symbols.count { it.ambiguous },
            symbols = symbols.take(MAX_OUTPUT_SYMBOLS),
            truncatedFiles = truncated,
        )
    }

    private fun isIdentifier(value: String): Boolean = value.isNotEmpty() &&
        (value.first().isLetter() || value.first() == '_' || value.first() == '$') &&
        value.all { it.isLetterOrDigit() || it == '_' || it == '$' }

    /** Lexer bounded by source size; strips comments and string contents from the index. */
    private fun tokenize(source: String): List<Token> {
        val result = ArrayList<Token>(minOf(source.length / 4, 16_384))
        var i = 0
        var line = 1
        while (i < source.length && result.size < MAX_TOKENS_PER_FILE) {
            val c = source[i]
            if (c == '\n') { line++; i++; continue }
            if (c.isWhitespace()) { i++; continue }
            if (c == '/' && source.getOrNull(i + 1) == '/') {
                while (i < source.length && source[i] != '\n') i++
                continue
            }
            if (c == '#') {
                while (i < source.length && source[i] != '\n') i++
                continue
            }
            if (c == '/' && source.getOrNull(i + 1) == '*') {
                i += 2
                var depth = 1
                while (i < source.length && depth > 0) {
                    if (source[i] == '\n') line++
                    if (source[i] == '/' && source.getOrNull(i + 1) == '*') { depth++; i += 2 }
                    else if (source[i] == '*' && source.getOrNull(i + 1) == '/') { depth--; i += 2 }
                    else i++
                }
                continue
            }
            if (c == '\'' || c == '"') {
                val quote = c
                val triple = source.getOrNull(i + 1) == quote && source.getOrNull(i + 2) == quote
                val delimiter = if (triple) 3 else 1
                i += delimiter
                while (i < source.length) {
                    if (source[i] == '\n') line++
                    if (!triple && source[i] == '\\') { i = (i + 2).coerceAtMost(source.length); continue }
                    if ((0 until delimiter).all { source.getOrNull(i + it) == quote }) { i += delimiter; break }
                    i++
                }
                continue
            }
            if (c.isLetter() || c == '_' || c == '$') {
                val start = i++
                while (i < source.length && (source[i].isLetterOrDigit() || source[i] == '_' || source[i] == '$')) i++
                result += Token(source.substring(start, i), line)
                continue
            }
            i++
        }
        return result
    }
}
