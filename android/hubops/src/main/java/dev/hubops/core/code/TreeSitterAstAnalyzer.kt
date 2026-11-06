package dev.hubops.core.code

/**
 * AST real via Tree-sitter. A integração usa reflexão para manter o núcleo compilável
 * sem carregar classes nativas em testes/ambientes que ainda não resolveram os AARs.
 * A ausência de grammar/JNI é reportada como indisponibilidade, nunca como sucesso.
 */
data class AstDeclaration(
    val name: String,
    val kind: String,
    val path: String,
    val line: Int,
)

data class AstFileResult(
    val path: String,
    val language: String,
    val nodeCount: Int,
    val syntaxErrorNodes: Int,
    val hasSyntaxErrors: Boolean,
    val truncated: Boolean,
    val declarations: List<AstDeclaration>,
)

data class AstProjectReport(
    val filesConsidered: Int,
    val filesOmitted: Int,
    val filesParsed: Int,
    val filesUnavailable: Int,
    val filesWithSyntaxErrors: Int,
    val truncatedFiles: Int,
    val totalNodes: Int,
    val declarations: List<AstDeclaration>,
    val fileResults: List<AstFileResult>,
    val unavailableReasons: List<String>,
) {
    fun toMarkdown(): String = buildString {
        appendLine("## AST real · Tree-sitter")
        appendLine("- Ficheiros considerados: $filesConsidered · omitidos por limite: $filesOmitted · analisados: $filesParsed · parser indisponível: $filesUnavailable")
        appendLine("- Ficheiros com erros sintáticos: $filesWithSyntaxErrors · AST limitada: $truncatedFiles · nós percorridos: $totalNodes · declarações: ${declarations.size}")
        fileResults.forEach { file ->
            appendLine("- `${file.path}` (${file.language}) — ${file.nodeCount} nós · ${file.syntaxErrorNodes} nós de erro${if (file.hasSyntaxErrors) " · SINTAXE COM ERROS" else ""}${if (file.truncated) " · LIMITE DE NÓS ATINGIDO" else ""}")
            file.declarations.take(40).forEach { d -> appendLine("  - ${d.kind} `${d.name}` · linha ${d.line}") }
        }
        unavailableReasons.distinct().forEach { appendLine("- Parser não disponível: $it") }
        appendLine("- AST sintática não equivale a resolução semântica de tipos, overloads, dispatch, reflection ou referências em runtime.")
    }
}

object TreeSitterAstAnalyzer {
    private const val MAX_FILES = 1_000
    private const val MAX_CHARS_PER_FILE = 1_000_000
    private const val MAX_NODES_PER_FILE = 100_000
    private const val MAX_DECLARATIONS = 5_000

    private data class Grammar(val language: String, val className: String, val declarationKinds: Map<String, String>)

    private val grammars = mapOf(
        "kt" to Grammar("Kotlin", "cn.enaium.treesitter.languages.kotlin.TreeSitterKotlin", mapOf(
            "class_declaration" to "classe", "object_declaration" to "object", "function_declaration" to "função",
            "property_declaration" to "propriedade", "type_alias" to "alias",
        )),
        "kts" to Grammar("Kotlin DSL", "cn.enaium.treesitter.languages.kotlin.TreeSitterKotlin", mapOf(
            "class_declaration" to "classe", "object_declaration" to "object", "function_declaration" to "função",
            "property_declaration" to "propriedade", "type_alias" to "alias",
        )),
        "java" to Grammar("Java", "cn.enaium.treesitter.languages.java.TreeSitterJava", mapOf(
            "class_declaration" to "classe", "interface_declaration" to "interface", "enum_declaration" to "enum",
            "record_declaration" to "record", "method_declaration" to "método", "constructor_declaration" to "construtor",
        )),
        "py" to Grammar("Python", "cn.enaium.treesitter.languages.python.TreeSitterPython", mapOf(
            "class_definition" to "classe", "function_definition" to "função",
        )),
    )

    fun analyze(files: Map<String, String>): AstProjectReport {
        val allCandidates = files.entries.asSequence()
            .filter { it.key.substringAfterLast('.', "").lowercase() in grammars }
            .sortedBy { it.key }
            .toList()
        val candidates = allCandidates.take(MAX_FILES)
        val results = mutableListOf<AstFileResult>()
        val unavailable = linkedSetOf<String>()
        val failedGrammars = mutableMapOf<String, String>()
        var unavailableFiles = 0
        var parsedFiles = 0
        var syntaxErrorFiles = 0
        var truncatedFiles = 0
        var totalNodes = 0
        val allDeclarations = mutableListOf<AstDeclaration>()

        candidates.forEach { (path, source) ->
            if (source.length > MAX_CHARS_PER_FILE) {
                unavailableFiles++
                unavailable += "$path excede o limite de 1 MB por ficheiro"
                return@forEach
            }
            val grammar = grammars.getValue(path.substringAfterLast('.', "").lowercase())
            failedGrammars[grammar.className]?.let { reason ->
                unavailableFiles++
                unavailable += reason
                return@forEach
            }
            try {
                val parsed = parseWithReflection(grammar, source, path)
                parsedFiles++
                if (parsed.hasSyntaxErrors) syntaxErrorFiles++
                if (parsed.truncated) truncatedFiles++
                totalNodes += parsed.nodeCount
                results += parsed
                allDeclarations += parsed.declarations
            } catch (error: ClassNotFoundException) {
                unavailableFiles++
                val reason = "${grammar.language}: dependência/grammar ${grammar.className} não encontrada"
                unavailable += reason
                failedGrammars[grammar.className] = reason
            } catch (error: LinkageError) {
                unavailableFiles++
                val reason = "${grammar.language}: biblioteca nativa/JNI indisponível (${error.javaClass.simpleName})"
                unavailable += reason
                failedGrammars[grammar.className] = reason
            } catch (error: java.lang.reflect.InvocationTargetException) {
                unavailableFiles++
                val cause = error.targetException
                val reason = if (cause is LinkageError) {
                    "${grammar.language}: biblioteca nativa/JNI indisponível (${cause.javaClass.simpleName})"
                } else {
                    "${grammar.language}: falha de execução do parser (${cause.javaClass.simpleName})"
                }
                unavailable += reason
                if (cause is LinkageError) failedGrammars[grammar.className] = reason
            } catch (error: ReflectiveOperationException) {
                unavailableFiles++
                val reason = "${grammar.language}: API Tree-sitter incompatível (${error.javaClass.simpleName})"
                unavailable += reason
                failedGrammars[grammar.className] = reason
            } catch (error: RuntimeException) {
                unavailableFiles++
                unavailable += "${grammar.language}: falha do parser (${error.javaClass.simpleName})"
            }
        }
        return AstProjectReport(
            filesConsidered = allCandidates.size,
            filesOmitted = (allCandidates.size - candidates.size).coerceAtLeast(0),
            filesParsed = parsedFiles,
            filesUnavailable = unavailableFiles,
            filesWithSyntaxErrors = syntaxErrorFiles,
            truncatedFiles = truncatedFiles,
            totalNodes = totalNodes,
            declarations = allDeclarations.take(MAX_DECLARATIONS),
            fileResults = results,
            unavailableReasons = unavailable.toList(),
        )
    }

    private fun parseWithReflection(grammar: Grammar, source: String, path: String): AstFileResult {
        val grammarClass = Class.forName(grammar.className)
        val languageFactory = grammarClass.methods.firstOrNull { it.name == "language" && it.parameterCount == 0 }
            ?: throw NoSuchMethodException("${grammar.className}.language()")
        val grammarReceiver = if (java.lang.reflect.Modifier.isStatic(languageFactory.modifiers)) null else {
            runCatching { grammarClass.getField("INSTANCE").get(null) }.getOrElse {
                runCatching { grammarClass.getField("Companion").get(null) }.getOrNull()
            }
        }
        val nativeLanguage = languageFactory.invoke(grammarReceiver)
        val languageClass = Class.forName("io.github.treesitter.ktreesitter.Language")
        val language = languageClass.constructors.firstOrNull { it.parameterCount == 1 }
            ?.newInstance(nativeLanguage) ?: throw NoSuchMethodException("Language(nativeLanguage)")
        val parserClass = Class.forName("io.github.treesitter.ktreesitter.Parser")
        val parser = parserClass.constructors.firstOrNull { it.parameterCount == 1 && it.parameterTypes[0].isAssignableFrom(languageClass) }
            ?.newInstance(language) ?: throw NoSuchMethodException("Parser(Language)")
        var tree: Any? = null
        try {
            val encodingClass = Class.forName("io.github.treesitter.ktreesitter.InputEncoding")
            val utf8 = encodingClass.enumConstants?.firstOrNull { it.toString() == "UTF_8" }
                ?: throw NoSuchFieldException("InputEncoding.UTF_8")
            val parseMethod = parserClass.methods.firstOrNull {
                it.name == "parse" && it.parameterCount == 3 &&
                    it.parameterTypes[0] == String::class.java && it.parameterTypes[1].isAssignableFrom(encodingClass)
            } ?: throw NoSuchMethodException("Parser.parse(String, InputEncoding, Tree?)")
            tree = parseMethod.invoke(parser, source, utf8, null)
            val root = tree!!.javaClass.methods.firstOrNull { it.name == "getRootNode" && it.parameterCount == 0 }
                ?.invoke(tree) ?: throw NoSuchMethodException("Tree.rootNode")
            val stack = ArrayDeque<Any>()
            stack.add(root)
            var nodes = 0
            var errors = 0
            val declarations = mutableListOf<AstDeclaration>()
            while (stack.isNotEmpty() && nodes < MAX_NODES_PER_FILE) {
                val node = stack.removeLast()
                nodes++
                val type = node.callString("getType")
                val isError = node.callBoolean("getIsError")
                val isMissing = node.callBoolean("getIsMissing")
                if (isError || isMissing) errors++
                val kind = grammar.declarationKinds[type]
                if (kind != null && declarations.size < MAX_DECLARATIONS) {
                    val nameNode = node.callNullable("childByFieldName", String::class.java, "name")
                        ?: node.callNullable("childByFieldName", String::class.java, "declarator")
                    val name = nameNode?.callNullable("text")?.toString()?.trim()?.takeIf { it.isNotBlank() }
                    if (name != null && name.length <= 160 && !name.contains('\n')) {
                        val point = node.callNullable("getStartPoint")
                        val row = point?.callNullable("getRow")?.toString()?.toIntOrNull() ?: 0
                        declarations += AstDeclaration(name, kind, path, row + 1)
                    }
                }
                val children = node.callNullable("getChildren") as? List<*> ?: emptyList<Any>()
                children.filterNotNull().forEach(stack::add)
            }
            val truncated = stack.isNotEmpty()
            val hasErrors = root.callBoolean("getHasError") || errors > 0
            return AstFileResult(path, grammar.language, nodes, errors, hasErrors, truncated, declarations)
        } finally {
            runCatching { tree?.javaClass?.getMethod("close")?.invoke(tree) }
            runCatching { parser.javaClass.getMethod("close").invoke(parser) }
        }
    }

    private fun Any.callBoolean(method: String): Boolean {
        val getter = javaClass.methods.firstOrNull { it.name == method && it.parameterCount == 0 }
            ?: if (method.startsWith("get")) javaClass.methods.firstOrNull {
                it.name == method.removePrefix("get").replaceFirstChar(Char::lowercase) && it.parameterCount == 0
            } else null
        return getter?.invoke(this) as? Boolean ?: false
    }

    private fun Any.callString(method: String): String =
        javaClass.methods.firstOrNull { it.name == method && it.parameterCount == 0 }?.invoke(this)?.toString().orEmpty()

    private fun Any.callNullable(method: String): Any? =
        javaClass.methods.firstOrNull { it.name == method && it.parameterCount == 0 }?.invoke(this)

    private fun Any.callNullable(method: String, parameter: Class<*>, value: Any): Any? =
        javaClass.methods.firstOrNull { it.name == method && it.parameterCount == 1 && it.parameterTypes[0] == parameter }
            ?.invoke(this, value)
}
