package dev.hubops.espelhar

import java.io.File
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import dev.hubops.core.code.AstProjectReport
import dev.hubops.core.code.TreeSitterAstAnalyzer

/** Motor local de análise incremental. Não executa código durante a análise. */
data class EspelharIssue(val type: String, val line: Int, val message: String, val severity: String)
data class EspelharSymbol(val type: String, val name: String, val line: Int)
data class EspelharAnalysis(
    val file: String, val language: String, val syntaxOk: Boolean,
    val issues: List<EspelharIssue>, val symbols: List<EspelharSymbol>,
    val errors: Int, val warnings: Int, val latencyMs: Long, val cached: Boolean,
)
data class EspelharPipelineTask(val name: String, val status: String, val detail: String)
data class EspelharPipelineReport(
    val tasks: List<EspelharPipelineTask>, val filesChecked: Int, val errors: Int, val warnings: Int,
    /** AST Tree-sitter real para Kotlin/Java/Python; indisponibilidade é reportada sem fingir sucesso. */
    val ast: AstProjectReport? = null,
    val projectGraph: EspelharProjectGraph? = null,
    val repairs: EspelharRepairReport? = null,
    val buildReadiness: EspelharBuildReadiness? = null,
)

class EspelharEngine(private val workspace: File) {
    private val cache = object : LinkedHashMap<String, Pair<String, EspelharAnalysis>>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<String, EspelharAnalysis>>?): Boolean = size > 128
    }
    private val supported = mapOf(
        "py" to "Python", "kt" to "Kotlin", "kts" to "Kotlin DSL", "java" to "Java",
        "js" to "JavaScript", "ts" to "TypeScript", "json" to "JSON", "xml" to "XML",
        "sh" to "Shell", "md" to "Markdown", "rs" to "Rust", "go" to "Go", "dart" to "Dart",
        "gradle" to "Gradle", "properties" to "Properties", "toml" to "TOML", "yaml" to "YAML", "yml" to "YAML",
    )

    fun analyze(path: String, source: String): EspelharAnalysis {
        val normalizedPath = validateRelativePath(path)
        require(source.length <= 500_000) { "O editor aceita no máximo 500 KB por ficheiro." }
        val ext = normalizedPath.substringAfterLast('.', "").lowercase()
        val language = supported[ext] ?: throw IllegalArgumentException("Linguagem não suportada: .$ext")
        val hash = sha256(source)
        synchronized(cache) { cache[normalizedPath]?.takeIf { it.first == hash }?.let { return it.second.copy(cached = true, latencyMs = 0) } }
        val started = System.nanoTime()
        val issues = mutableListOf<EspelharIssue>()
        val symbols = extractSymbols(source, ext)
        issues += bracketIssues(source, ext)
        if (ext == "py") {
            if (Regex("(?m)^\\s*except\\s*:").containsMatchIn(source))
                issues += issue("bare_except", source, Regex("(?m)^\\s*except\\s*:").find(source)!!.range.first, "except: sem tipo específico", "warning")
            Regex("(?m)^\\s*def\\s+([A-Za-z_]\\w*)\\s*\\([^)]*\\b\\w+\\s*=\\s*(\\[\\]|\\{\\}|set\\(\\))").findAll(source).forEach {
                issues += issue("mutable_default", source, it.range.first, "Valor padrão mutável em '${it.groupValues[1]}'", "error")
            }
            Regex("(?m)^\\s*(return|raise)\\b[^\\n]*\\n([ \\t]+)(?!\\s*(?:#|$))([^\\n]+)").findAll(source).forEach {
                issues += issue("dead_code", source, it.range.last - it.groupValues[3].length + 1, "Possível código inalcançável após ${it.groupValues[1]}", "warning")
            }
            val syntax = pythonDelimiterSyntax(source)
            if (syntax != null) issues += syntax
        }
        if (ext == "json" && runCatching {
                val tokener = org.json.JSONTokener(source)
                tokener.nextValue()
                tokener.nextClean() == '\u0000'
            }.getOrDefault(false).not())
            issues += EspelharIssue("invalid_json", 1, "JSON inválido, incompleto ou com conteúdo após o valor principal", "error")
        if (ext == "xml" && (source.count { it == '<' } != source.count { it == '>' }))
            issues += EspelharIssue("xml_delimiter", 1, "Possível tag XML incompleta", "warning")
        val syntaxOk = issues.none { it.severity == "error" && it.type in setOf("unclosed_bracket", "mismatched_bracket", "python_syntax", "invalid_json") }
        val result = EspelharAnalysis(normalizedPath, language, syntaxOk, issues.distinctBy { Triple(it.type, it.line, it.message) }.sortedBy { it.line }, symbols,
            issues.count { it.severity == "error" }, issues.count { it.severity == "warning" },
            ((System.nanoTime() - started) / 1_000_000).coerceAtLeast(0), false)
        synchronized(cache) { cache[normalizedPath] = hash to result }
        return result
    }

    fun save(path: String, source: String): File {
        val target = safeFile(path)
        require(source.length <= 500_000) { "O ficheiro excede 500 KB." }
        target.parentFile?.let { require(it.exists() || it.mkdirs()) { "Não foi possível criar a pasta." } }
        // Gravação atómica: um encerramento inesperado não deixa o ficheiro final parcialmente escrito.
        val temp = File(target.parentFile, ".${target.name}.${System.nanoTime()}.tmp")
        try {
            temp.writeText(source, Charsets.UTF_8)
            require(temp.length() <= 2_000_000) { "O ficheiro temporário excedeu o limite de segurança." }
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { if (temp.exists()) temp.delete() }
        synchronized(cache) { cache.remove(validateRelativePath(path)) }
        return target
    }

    fun read(path: String): String = safeFile(path).takeIf { it.isFile }?.readText(Charsets.UTF_8)
        ?: throw IllegalArgumentException("Ficheiro não encontrado: $path")

    /** Importa apenas fontes/texto de um ZIP do Despertar. Valida tudo antes de gravar. */
    fun importZip(source: File, destinationPrefix: String = "projects/despertar"): Int {
        require(source.isFile && source.length() in 1..(30L * 1024 * 1024)) {
            "ZIP inexistente, vazio ou superior a 30 MB."
        }
        val prefix = validateRelativePath(destinationPrefix).trimEnd('/')
        val staged = linkedMapOf<String, ByteArray>()
        var expanded = 0L
        var entries = 0
        ZipInputStream(source.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries++
                require(entries <= 1_000) { "O ZIP excede 1.000 entradas." }
                if (!entry.isDirectory) {
                    val raw = entry.name.replace('\\', '/').trimEnd('/')
                    require(!raw.startsWith('/') && raw.split('/').none { it == ".." || it.isBlank() }) {
                        "O ZIP contém um caminho absoluto ou traversal."
                    }
                    val ext = raw.substringAfterLast('.', "").lowercase()
                    val supportedSource = ext in supported.keys && raw.split('/').none { it.startsWith(".") }
                    val clean = if (supportedSource) validateRelativePath(raw) else null
                    val output = if (supportedSource) java.io.ByteArrayOutputStream() else null
                    val buffer = ByteArray(8 * 1024)
                    var count = 0
                    while (true) {
                        val n = zip.read(buffer)
                        if (n < 0) break
                        count += n
                        expanded += n
                        require(expanded <= 20L * 1024 * 1024 && (!supportedSource || count <= 500_000)) {
                            "O ZIP excede os limites de 500 KB por fonte / 20 MB expandidos no total."
                        }
                        output?.write(buffer, 0, n)
                    }
                    if (supportedSource) {
                        val targetPath = "$prefix/$clean"
                        safeFile(targetPath)
                        require(!staged.containsKey(targetPath)) { "Entrada duplicada no ZIP: $clean" }
                        staged[targetPath] = output!!.toByteArray()
                    }
                }
                zip.closeEntry()
            }
        }
        require(staged.isNotEmpty()) { "O ZIP não contém ficheiros de texto com extensões suportadas." }
        require(staged.size <= 200) { "A importação excede 200 ficheiros suportados." }
        val targets = staged.keys.map { it to safeFile(it) }
        require(targets.none { it.second.exists() }) { "A importação foi cancelada para não substituir ficheiros existentes." }
        val written = mutableListOf<File>()
        try {
            targets.forEach { (path, target) ->
                target.parentFile?.let { require(it.exists() || it.mkdirs()) { "Não foi possível criar a pasta de importação." } }
                written += target
                target.writeBytes(staged.getValue(path))
            }
        } catch (failure: Exception) {
            written.forEach { it.delete() }
            throw failure
        }
        synchronized(cache) { staged.keys.forEach { cache.remove(it) } }
        return staged.size
    }

    /** Importa snapshot GitHub somente-leitura; valida todos os caminhos antes de gravar e nunca substitui ficheiros. */
    fun importSources(sources: Map<String, String>, destinationPrefix: String): Int {
        require(sources.isNotEmpty() && sources.size <= 40) { "Snapshot vazio ou superior a 40 ficheiros." }
        val prefix = validateRelativePath(destinationPrefix).trimEnd('/')
        val staged = linkedMapOf<String, ByteArray>()
        sources.forEach { (path, text) ->
            val relative = validateRelativePath(path)
            require(relative.substringAfterLast('.', "").lowercase() in supported.keys) { "Extensão não suportada: $relative" }
            require(text.length <= 500_000) { "Ficheiro excede 500 KB: $relative" }
            val destination = "$prefix/$relative"
            validateRelativePath(destination)
            staged[destination] = text.toByteArray(Charsets.UTF_8)
        }
        require(staged.values.sumOf { it.size.toLong() } <= 10L * 1024 * 1024) { "Snapshot excede 10 MB." }
        val targets = staged.keys.map { it to safeFile(it) }
        require(targets.none { it.second.exists() }) { "Importação cancelada para não substituir ficheiros existentes." }
        val written = mutableListOf<File>()
        try {
            targets.forEach { (path, target) ->
                target.parentFile?.let { require(it.exists() || it.mkdirs()) { "Não foi possível criar pasta." } }
                val temp = File(target.parentFile, ".${target.name}.import.tmp")
                try {
                    temp.writeBytes(staged.getValue(path))
                    try { Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE) }
                    catch (_: AtomicMoveNotSupportedException) { Files.move(temp.toPath(), target.toPath()) }
                    written += target
                } finally { temp.delete() }
            }
        } catch (failure: Exception) {
            written.forEach { it.delete() }
            throw failure
        }
        synchronized(cache) { staged.keys.forEach { cache.remove(it) } }
        return staged.size
    }

    fun pipeline(prefix: String? = null): EspelharPipelineReport {
        val workspaceRoot = workspace.canonicalFile
        val root = if (prefix.isNullOrBlank()) workspaceRoot else safePath(prefix)
        require(root.path == workspaceRoot.path || root.path.startsWith(workspaceRoot.path + File.separator)) {
            "A pasta de análise sai do workspace."
        }
        require(root.isDirectory) { "Pasta de análise inexistente: ${prefix ?: "workspace"}." }
        val files = root.walkTopDown().onEnter { dir ->
            dir == root || (!dir.name.startsWith(".") && dir.name !in setOf("build", ".gradle", "node_modules"))
        }.filter { it.isFile && it.extension.lowercase() in supported.keys }
            .take(2_001).toList().sortedBy { it.relativeTo(root).invariantSeparatorsPath }
        val tasks = mutableListOf<EspelharPipelineTask>()
        if (files.size > 200) tasks += EspelharPipelineTask("limite", "warning", "Mais de 200 ficheiros; análise limitada aos primeiros 200.")
        var errors = 0; var warnings = 0; var totalBytes = 0L
        val astSources = linkedMapOf<String, String>()
        val repairSources = linkedMapOf<String, String>()
        files.take(200).forEach { file ->
            val rel = file.relativeTo(root).invariantSeparatorsPath
            if (file.length() > 500_000 || totalBytes + file.length() > 10L * 1024 * 1024) {
                warnings++
                tasks += EspelharPipelineTask(rel, "skipped", "Limite de tamanho excedido (500 KB por ficheiro / 10 MB por pipeline).")
            } else {
                totalBytes += file.length()
                val source = runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
                val result = source?.let { runCatching { analyze(rel, it) }.getOrNull() }
                if (source != null) {
                    if (file.extension.lowercase() in setOf("kt", "kts", "java", "py")) astSources[rel] = source
                    repairSources[rel] = source
                }
                if (result == null) {
                    errors++; tasks += EspelharPipelineTask(rel, "failed", "Não foi possível ler/analisar o ficheiro.")
                } else {
                    errors += result.errors; warnings += result.warnings
                    tasks += EspelharPipelineTask(rel, if (result.errors > 0) "failed" else if (result.warnings > 0) "warning" else "ok",
                        "${result.errors} erro(s), ${result.warnings} aviso(s)")
                }
            }
        }
        if (files.isEmpty()) tasks += EspelharPipelineTask("workspace", "info", "Ainda não existem ficheiros suportados no espaço de trabalho.")
        val ast = runCatching { TreeSitterAstAnalyzer.analyze(astSources) }.getOrNull()
        if (ast != null) {
            val astStatus = when {
                ast.filesParsed == 0 && ast.filesUnavailable > 0 -> "unavailable"
                ast.filesWithSyntaxErrors > 0 -> "failed"
                ast.filesUnavailable > 0 || ast.truncatedFiles > 0 -> "warning"
                else -> "ok"
            }
            tasks += EspelharPipelineTask("AST · Tree-sitter", astStatus,
                "${ast.filesParsed} analisados · ${ast.filesUnavailable} indisponíveis · ${ast.filesWithSyntaxErrors} com erros sintáticos · ${ast.declarations.size} declarações")
        }
        val graph = runCatching { EspelharProjectServices.dependencyGraph(astSources) }.getOrNull()
        val repairs = runCatching { EspelharProjectServices.proposeRepairs(repairSources) }.getOrNull()
        val buildInventory = root.walkTopDown()
            .onEnter { dir -> dir == root || (!dir.name.startsWith(".") && dir.name !in setOf("build", ".gradle", "node_modules")) }
            .filter { it.isFile }.take(2_001)
            .associate { it.relativeTo(root).invariantSeparatorsPath to "" }
        val buildReadiness = runCatching { EspelharProjectServices.buildReadiness(buildInventory) }.getOrNull()
        graph?.let { tasks += EspelharPipelineTask("Mapa de dependências", if (it.unresolved > 0 || it.ambiguous > 0) "warning" else "ok",
            "${it.files.size} ficheiros · ${it.resolved} imports associados · ${it.unresolved} não resolvidos · ${it.ambiguous} ambíguos") }
        repairs?.let { tasks += EspelharPipelineTask("Repair Engine", if (it.candidates.isNotEmpty()) "review" else "ok",
            "${it.candidates.size} propostas de normalização; nenhuma aplicada automaticamente") }
        buildReadiness?.let { readiness -> tasks += EspelharPipelineTask("Build Android · pré-verificação", readiness.status,
            "${readiness.blockers.size} bloqueios · ${readiness.warnings.size} avisos · ${readiness.sourceFiles} fontes; nenhuma compilação foi executada") }
        return EspelharPipelineReport(tasks, files.size.coerceAtMost(200), errors, warnings, ast, graph, repairs, buildReadiness)
    }

    /** Descarta apenas uma pasta isolada de staging criada pelo fluxo de integração. */
    fun discardIntegrationStaging(stagingPrefix: String): Boolean {
        require(stagingPrefix.startsWith("integration-staging/")) { "Só é permitido descartar pastas de staging da integração." }
        val root = workspace.canonicalFile
        val stage = safePath(stagingPrefix)
        require(stage.path.startsWith(root.path + File.separator) && stage != root) { "Staging fora do workspace." }
        if (!stage.exists()) return true
        stage.walkBottomUp().forEach { item ->
            if (!java.nio.file.Files.isSymbolicLink(item.toPath())) {
                require(item.canonicalPath == stage.path || item.canonicalPath.startsWith(stage.path + File.separator)) {
                    "Staging contém caminho que escapa da pasta; descarte recusado."
                }
            }
            require(item.delete()) { "Não foi possível remover ${item.name} do staging." }
        }
        return true
    }

    /** Exporta um snapshot de projeto para análise no Despertar; não modifica o workspace. */
    fun exportProjectZip(prefix: String, destination: File): File {
        val root = workspace.canonicalFile
        val sourceRoot = safePath(prefix)
        require(sourceRoot.path.startsWith(root.path + File.separator) && sourceRoot.isDirectory) {
            "Pasta de projeto inexistente ou fora do workspace."
        }
        val files = sourceRoot.walkTopDown()
            .onEnter { dir -> dir == sourceRoot || (!dir.name.startsWith(".") && dir.name !in setOf("build", ".gradle", "node_modules", "integration-staging")) }
            .filter { it.isFile && it.extension.lowercase() in supported.keys }
            .take(201).toList()
        require(files.isNotEmpty() && files.size <= 200) { "Projeto vazio ou superior a 200 ficheiros." }
        require(files.all { it.canonicalPath.startsWith(sourceRoot.path + File.separator) }) { "Projeto contém link para fora da pasta." }
        var totalBytes = 0L
        files.forEach { file ->
            require(file.length() <= 500_000) { "Ficheiro superior a 500 KB: ${file.name}" }
            totalBytes += file.length()
            require(totalBytes <= 10L * 1024 * 1024) { "Projeto excede 10 MB de fontes." }
        }
        destination.parentFile?.let { require(it.exists() || it.mkdirs()) { "Não foi possível criar a pasta temporária de exportação." } }
        val temp = File(destination.parentFile, ".${destination.name}.${System.nanoTime()}.tmp")
        try {
            ZipOutputStream(temp.outputStream().buffered()).use { zip ->
                files.sortedBy { it.relativeTo(sourceRoot).invariantSeparatorsPath }.forEach { file ->
                    val relative = file.relativeTo(sourceRoot).invariantSeparatorsPath
                    validateRelativePath(relative)
                    zip.putNextEntry(ZipEntry(relative))
                    file.inputStream().buffered().use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            require(temp.length() in 1..(30L * 1024 * 1024)) { "Snapshot ZIP excede o limite permitido." }
            Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally { temp.delete() }
        return destination
    }

    /** Promove uma importação previamente validada com um rename de diretório, sem substituir ficheiros. */
    fun promoteStagedProject(stagingPrefix: String, destinationPrefix: String): Int {
        val root = workspace.canonicalFile
        val stage = safePath(stagingPrefix)
        val destination = safePath(destinationPrefix)
        require(stage.path.startsWith(root.path + File.separator) && stage.isDirectory) { "Staging inexistente ou fora do workspace." }
        require(destination.path.startsWith(root.path + File.separator) && stage.path != destination.path &&
            !destination.path.startsWith(stage.path + File.separator)) { "Destino inválido ou sobreposto ao staging." }
        val nodes = stage.walkTopDown().toList()
        require(nodes.none { java.nio.file.Files.isSymbolicLink(it.toPath()) }) { "Staging contém link simbólico; promoção recusada." }
        val files = nodes.filter { it.isFile }.take(201)
        require(files.isNotEmpty() && files.size <= 200) { "Staging vazio ou superior a 200 ficheiros." }
        require(files.all { it.canonicalPath.startsWith(stage.path + File.separator) }) {
            "Staging contém link para fora da pasta."
        }
        require(!destination.exists()) { "Destino já existe; promoção recusada para não substituir conteúdo." }
        destination.parentFile?.let { require(it.exists() || it.mkdirs()) { "Não foi possível criar diretório de destino." } }
        try {
            Files.move(stage.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(stage.toPath(), destination.toPath())
        }
        val promotedCount = if (destination.isDirectory) destination.walkTopDown().count { it.isFile } else -1
        if (promotedCount != files.size) {
            runCatching {
                try { Files.move(destination.toPath(), stage.toPath(), StandardCopyOption.ATOMIC_MOVE) }
                catch (_: AtomicMoveNotSupportedException) { Files.move(destination.toPath(), stage.toPath()) }
            }
            error("A verificação da pasta promovida falhou; a pasta foi devolvida ao staging quando possível.")
        }
        synchronized(cache) { cache.keys.filter { it == stagingPrefix || it.startsWith("$stagingPrefix/") }.forEach(cache::remove) }
        return files.size
    }

    fun capabilities() = mapOf("offline" to true, "incrementalCache" to true, "languages" to supported.values.toList(),
        "analysis" to "heurística estática local", "execution" to "ShellRunner Android UID; não é sandbox proot/container")

    private fun validateRelativePath(path: String): String {
        require(path.isNotBlank() && path.length <= 240 && !path.startsWith('/') &&
            !path.contains('\\') && !path.contains('\u0000') && path.none { it.isISOControl() }) { "Caminho relativo inválido." }
        val parts = path.split('/')
        require(parts.all { it.isNotBlank() && it != "." && it != ".." && !it.startsWith(".") }) {
            "O caminho contém segmentos ocultos ou inválidos."
        }
        return parts.joinToString("/")
    }

    private fun safePath(path: String): File {
        val clean = validateRelativePath(path)
        val root = workspace.canonicalFile
        var cursor = root
        clean.split('/').forEach { segment ->
            cursor = File(cursor, segment)
            if (java.nio.file.Files.exists(cursor.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                require(!java.nio.file.Files.isSymbolicLink(cursor.toPath())) { "O caminho contém link simbólico: $segment" }
            }
        }
        val canonical = cursor.canonicalFile
        require(canonical.path.startsWith(root.path + File.separator)) { "O caminho sai do workspace." }
        return canonical
    }

    private fun safeFile(path: String): File {
        val target = safePath(path)
        val ext = target.extension.lowercase()
        require(ext in supported.keys) { "Extensão não suportada." }
        return target
    }

    private fun extractSymbols(src: String, ext: String): List<EspelharSymbol> {
        val patterns = when (ext) {
            "py" -> listOf("(?m)^\\s*class\\s+([A-Za-z_]\\w*)" to "class", "(?m)^\\s*async\\s+def\\s+([A-Za-z_]\\w*)" to "async_function", "(?m)^\\s*def\\s+([A-Za-z_]\\w*)" to "function")
            "kt", "kts" -> listOf("(?m)^\\s*class\\s+([A-Za-z_]\\w*)" to "class", "(?m)^\\s*fun\\s+([A-Za-z_]\\w*)" to "function", "(?m)^\\s*object\\s+([A-Za-z_]\\w*)" to "object")
            "java" -> listOf("(?m)^\\s*(?:public|protected|private)?\\s*class\\s+([A-Za-z_]\\w*)" to "class", "(?m)^\\s*(?:public|protected|private)\\s+[\\w<>\\[\\]]+\\s+([A-Za-z_]\\w*)\\s*\\(" to "method")
            else -> listOf("(?m)^\\s*(?:export\\s+)?(?:async\\s+)?function\\s+([A-Za-z_$][\\w$]*)" to "function", "(?m)^\\s*class\\s+([A-Za-z_$][\\w$]*)" to "class", "(?m)^\\s*(?:const|let|var)\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*(?:async\\s*)?\\(" to "function")
        }
        return patterns.flatMap { (pattern, type) -> Regex(pattern).findAll(src).map { EspelharSymbol(type, it.groupValues[1], src.substring(0, it.range.first).count { c -> c == '\n' } + 1) } }
            .sortedBy { it.line }.take(500)
    }

    private fun bracketIssues(src: String, ext: String): List<EspelharIssue> {
        val stack = ArrayDeque<Pair<Char, Int>>(); val out = mutableListOf<EspelharIssue>()
        var line = 1; var quote: Char? = null; var tripleQuote: Char? = null
        var escaped = false; var lineComment = false; var blockComment = false
        var i = 0
        while (i < src.length) {
            val c = src[i]; val n = src.getOrNull(i + 1)
            if (c == '\n') { line++; lineComment = false; i++; continue }
            if (lineComment) { i++; continue }
            if (blockComment) { if (c == '*' && n == '/') { blockComment = false; i += 2 } else i++; continue }
            if (tripleQuote != null) {
                if (c == tripleQuote && n == tripleQuote && src.getOrNull(i + 2) == tripleQuote) {
                    tripleQuote = null; i += 3
                } else i++
                continue
            }
            if (quote != null) { if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == quote) quote = null; i++; continue }
            if (c == '/' && n == '/') { lineComment = true; i += 2; continue }
            if (c == '#' && ext in setOf("py", "sh", "yaml", "yml", "toml", "properties")) { lineComment = true; i++; continue }
            if (c == '/' && n == '*') { blockComment = true; i += 2; continue }
            if (c in "'\"" && ext in setOf("py", "kt", "kts") && n == c && src.getOrNull(i + 2) == c) {
                tripleQuote = c; i += 3; continue
            }
            if (c == '\'' || c == '"' || (c == '`' && ext in setOf("js", "ts"))) { quote = c; i++; continue }
            if (c in "([{ ".trim()) stack.add(c to line)
            else if (c in ")]}") {
                val expected = mapOf(')' to '(', ']' to '[', '}' to '{')[c]
                if (stack.lastOrNull()?.first == expected) stack.removeLast()
                else out += EspelharIssue("mismatched_bracket", line, "Delimitador '$c' sem abertura correspondente", "error")
            }
            i++
        }
        stack.forEach { (c, ln) -> out += EspelharIssue("unclosed_bracket", ln, "Delimitador '$c' não fechado", "error") }
        return out
    }

    private fun pythonDelimiterSyntax(src: String): EspelharIssue? {
        val trimmed = src.trimEnd()
        if (trimmed.endsWith(":") && trimmed.lines().last().trim().startsWith("def "))
            return EspelharIssue("python_syntax", trimmed.lines().size, "Definição incompleta: falta o corpo da função", "error")
        return null
    }
    private fun issue(type: String, src: String, offset: Int, msg: String, severity: String) =
        EspelharIssue(type, src.substring(0, offset.coerceIn(0, src.length)).count { it == '\n' } + 1, msg, severity)
    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
