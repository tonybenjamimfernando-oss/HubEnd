package dev.hubops.espelhar

import java.io.File
import java.security.MessageDigest

/** Relações importadas entre ficheiros. São evidência estrutural, não prova de resolução do compilador. */
data class EspelharFileNode(val path: String, val packageName: String, val declarations: List<String>)
data class EspelharImportEdge(val from: String, val importedName: String, val candidates: List<String>, val status: String)
data class EspelharProjectGraph(
    val files: List<EspelharFileNode>,
    val edges: List<EspelharImportEdge>,
    val resolved: Int,
    val unresolved: Int,
    val ambiguous: Int,
) {
    fun toMarkdown(): String = buildString {
        appendLine("## Mapa de dependências (análise estática)")
        appendLine("- Ficheiros: ${files.size}; imports associados: $resolved; não resolvidos: $unresolved; ambíguos: $ambiguous")
        edges.forEach { appendLine("- ${it.status}: `${it.from}` → `${it.importedName}`${if (it.candidates.isEmpty()) "" else " (${it.candidates.joinToString()})"}") }
        appendLine("- A associação é conservadora e não substitui a resolução de símbolos do compilador.")
    }
}

data class EspelharRepair(val id: String, val path: String, val title: String, val detail: String, val beforeHash: String, val replacement: String)

data class EspelharSnapshotDiff(
    val added: List<String>,
    val changed: List<String>,
    val unchanged: List<String>,
    val localOnly: List<String>,
) {
    val hasChanges: Boolean get() = added.isNotEmpty() || changed.isNotEmpty() || localOnly.isNotEmpty()
    fun toMarkdown(): String = buildString {
        appendLine("## Comparação de snapshot (somente leitura)")
        appendLine("- Novos: ${added.size}; alterados: ${changed.size}; iguais: ${unchanged.size}; apenas locais: ${localOnly.size}")
        added.forEach { appendLine("- ADICIONAR: `$it`") }
        changed.forEach { appendLine("- ALTERADO: `$it`") }
        localOnly.forEach { appendLine("- APENAS LOCAL: `$it`") }
        appendLine("- Nenhum ficheiro foi criado, removido ou modificado por esta comparação.")
    }
}
data class EspelharBuildReadiness(
    val status: String,
    val androidManifest: Boolean,
    val rootBuildFile: Boolean,
    val settingsFile: Boolean,
    val appBuildFile: Boolean,
    val wrapperScript: Boolean,
    val wrapperJar: Boolean,
    val wrapperProperties: Boolean,
    val sourceFiles: Int,
    val blockers: List<String>,
    val warnings: List<String>,
) {
    fun toMarkdown(): String = buildString {
        appendLine("## Pré-verificação de build Android (não executa Gradle)")
        appendLine("- Estado: $status; fontes: $sourceFiles")
        appendLine("- Manifesto: $androidManifest; build raiz: $rootBuildFile; settings: $settingsFile; build app: $appBuildFile")
        appendLine("- Wrapper: script=$wrapperScript; JAR=$wrapperJar; propriedades=$wrapperProperties")
        blockers.forEach { appendLine("- BLOQUEIO: $it") }
        warnings.forEach { appendLine("- AVISO: $it") }
    }
}

data class EspelharRepairReport(val candidates: List<EspelharRepair>, val filesInspected: Int) {
    fun toMarkdown(): String = buildString {
        appendLine("## Reparações seguras propostas")
        appendLine("- Ficheiros inspecionados: $filesInspected; propostas: ${candidates.size}")
        candidates.forEach { appendLine("- `${it.id}` · `${it.path}` — ${it.title}: ${it.detail}") }
        appendLine("- Nenhuma proposta é aplicada automaticamente. A aplicação exige confirmação e validação do hash original.")
    }
}

object EspelharProjectServices {
    private val importPattern = Regex("(?m)^\\s*import\\s+(?:static\\s+)?([A-Za-z_][\\w.]*)")
    private val packagePattern = Regex("(?m)^\\s*package\\s+([A-Za-z_][\\w.]*)")
    private val declarationPattern = Regex("(?m)^\\s*(?:(?:public|private|protected|internal|open|abstract|data|sealed|final|static)\\s+)*(?:class|interface|object|enum\\s+class|record|fun|def)\\s+([A-Za-z_][A-Za-z0-9_]*)")

    /** Verifica pré-condições comuns de um projeto Android sem alegar que o build foi executado. */
    fun buildReadiness(files: Map<String, String>): EspelharBuildReadiness {
        val paths = files.keys.map { it.replace('\\', '/').trimStart('/') }.toSet()
        fun hasExactOrSuffix(name: String) = paths.any { it == name || it.endsWith("/$name") }
        val manifest = hasExactOrSuffix("AndroidManifest.xml")
        val rootBuild = paths.any { it == "build.gradle" || it == "build.gradle.kts" }
        val settings = paths.any { it == "settings.gradle" || it == "settings.gradle.kts" }
        val appBuild = paths.any { it == "app/build.gradle" || it == "app/build.gradle.kts" }
        val wrapperScript = hasExactOrSuffix("gradlew")
        val wrapperJar = paths.any { it.endsWith("gradle/wrapper/gradle-wrapper.jar") }
        val wrapperProperties = paths.any { it.endsWith("gradle/wrapper/gradle-wrapper.properties") }
        val sources = paths.count { it.substringAfterLast('.', "").lowercase() in setOf("kt", "java") }
        val blockers = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        if (!manifest) blockers += "AndroidManifest.xml não encontrado."
        if (!rootBuild) blockers += "build.gradle(.kts) da raiz não encontrado."
        if (!settings) blockers += "settings.gradle(.kts) não encontrado."
        if (!appBuild) warnings += "Build file do módulo app não localizado; confirme a estrutura do projeto."
        if (sources == 0) blockers += "Nenhum ficheiro Kotlin/Java foi encontrado."
        if (!(wrapperScript && wrapperJar && wrapperProperties)) warnings += "Gradle Wrapper incompleto; é necessário fornecer os três artefactos oficiais ou usar uma instalação Gradle compatível."
        val status = when { blockers.isNotEmpty() -> "blocked"; warnings.isNotEmpty() -> "review"; else -> "preflight-ready" }
        return EspelharBuildReadiness(status, manifest, rootBuild, settings, appBuild, wrapperScript, wrapperJar, wrapperProperties, sources, blockers, warnings)
    }

    fun dependencyGraph(sources: Map<String, String>): EspelharProjectGraph {
        val nodes = sources.entries.sortedBy { it.key }.map { (path, source) ->
            EspelharFileNode(path, packagePattern.find(source)?.groupValues?.get(1).orEmpty(),
                declarationPattern.findAll(source).map { it.groupValues[1] }.distinct().toList())
        }
        val packageIndex = nodes.filter { it.packageName.isNotBlank() }.groupBy { it.packageName }
        val edges = sources.entries.sortedBy { it.key }.flatMap { (path, source) ->
            importPattern.findAll(source).map { match ->
                val imported = match.groupValues[1]
                val wildcard = imported.endsWith(".*")
                val owner = if (wildcard) imported.removeSuffix(".*") else imported.substringBeforeLast('.', "")
                val simple = imported.substringAfterLast('.')
                // A normal class import must resolve to that declaration in the stated package.
                // Merely sharing a package is not evidence that every file in it is the imported symbol.
                val candidates = if (wildcard) {
                    packageIndex[owner].orEmpty().map { it.path }
                } else {
                    nodes.filter { it.packageName == owner && simple in it.declarations }.map { it.path }
                }.filter { it != path }.distinct().sorted()
                val status = when {
                    candidates.isEmpty() -> "unresolved"
                    wildcard -> "package-associated"
                    candidates.size == 1 -> "associated"
                    else -> "ambiguous"
                }
                EspelharImportEdge(path, imported, candidates, status)
            }.toList()
        }
        return EspelharProjectGraph(nodes, edges, edges.count { it.status == "associated" || it.status == "package-associated" },
            edges.count { it.status == "unresolved" }, edges.count { it.status == "ambiguous" })
    }

    /** Compara snapshot remoto com uma pasta local do workspace sem alterar nenhum dos lados. */
    fun compareSnapshotToWorkspace(workspace: File, sources: Map<String, String>, destinationPrefix: String): EspelharSnapshotDiff {
        require(sources.size <= 200) { "A comparação excede 200 ficheiros." }
        val prefix = destinationPrefix.trimEnd('/')
        require(prefix.isNotBlank()) { "Pasta de comparação inválida." }
        require(!prefix.startsWith('/') && !prefix.contains('\\') && prefix.split('/').none { it.isBlank() || it == "." || it == ".." || it.startsWith(".") }) {
            "A pasta de comparação contém um caminho inválido."
        }
        val workspaceRoot = workspace.canonicalFile
        val root = File(workspaceRoot, prefix).canonicalFile
        require(root.path.startsWith(workspaceRoot.path + File.separator)) { "A pasta de comparação sai do workspace." }
        var remoteBytes = 0L
        val remote = sources.entries.associate { (path, content) ->
            require(path.isNotBlank() && !path.startsWith('/') && !path.contains('\\') &&
                path.split('/').none { it.isBlank() || it == "." || it == ".." || it.startsWith(".") }) { "Snapshot contém caminho inválido." }
            require(content.length <= 500_000) { "Snapshot contém ficheiro superior a 500 KB." }
            remoteBytes += content.toByteArray(Charsets.UTF_8).size
            require(remoteBytes <= 10L * 1024 * 1024) { "Snapshot excede 10 MB." }
            path to content
        }
        require(remote.size == sources.size) { "O snapshot contém caminhos duplicados após normalização." }
        val local = if (root.exists()) {
            require(root.isDirectory) { "O destino local não é uma pasta." }
            root.walkTopDown()
                .onEnter { dir -> dir == root || (!dir.name.startsWith(".") && dir.name !in setOf("build", ".gradle", "node_modules")) }
                .filter { it.isFile }
                .take(201)
                .associate { file ->
                    require(file.canonicalPath.startsWith(root.canonicalPath + File.separator)) { "A pasta local contém um link para fora do workspace." }
                    file.relativeTo(root).invariantSeparatorsPath to file
                }
                .also { require(it.size <= 200) { "A pasta local excede 200 ficheiros." } }
        } else emptyMap()
        val added = mutableListOf<String>()
        val changed = mutableListOf<String>()
        val unchanged = mutableListOf<String>()
        remote.toSortedMap().forEach { (path, content) ->
            val existing = local[path]
            when {
                existing == null -> added += path
                existing.length() > 1_000_000 -> changed += path
                sha256(existing.readText(Charsets.UTF_8)) == sha256(content) -> unchanged += path
                else -> changed += path
            }
        }
        val localOnly = (local.keys - remote.keys).sorted()
        return EspelharSnapshotDiff(added, changed, unchanged, localOnly)
    }

    /** Sugere apenas normalizações determinísticas de baixo risco; nunca reescreve o projeto. */
    fun proposeRepairs(sources: Map<String, String>): EspelharRepairReport {
        val proposals = mutableListOf<EspelharRepair>()
        sources.toSortedMap().forEach { (path, source) ->
            if (source.startsWith('\uFEFF')) {
                val replacement = source.removePrefix("\uFEFF")
                proposals += repair(path, "remove-utf8-bom", "Remover BOM UTF-8", "Remove apenas o marcador BOM inicial.", source, replacement)
            }
            val withoutTrailing = source.lineSequence().joinToString("\n") { it.trimEnd(' ', '\t') }
            if (withoutTrailing != source.replace("\r\n", "\n").replace('\r', '\n')) {
                val normalized = source.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
                    .lineSequence().joinToString("\n") { it.trimEnd(' ', '\t') }
                proposals += repair(path, "normalize-whitespace", "Normalizar espaços e finais de linha",
                    "Remove espaços no fim das linhas e normaliza CRLF/CR para LF.", source, normalized)
            }
            if (source.isNotEmpty() && !source.endsWith('\n')) {
                val normalized = source.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
                    .lineSequence().joinToString("\n") { it.trimEnd(' ', '\t') } + "\n"
                proposals += repair(path, "final-newline", "Adicionar quebra de linha final",
                    "Acrescenta uma única quebra de linha final.", source, normalized)
            }
        }
        return EspelharRepairReport(proposals.distinctBy { it.path to it.id }, sources.size)
    }

    /**
     * Reverte uma reparação apenas se o ficheiro ainda contiver exatamente o resultado aplicado
     * e a cópia de segurança corresponder ao hash original. A operação não apaga a evidência:
     * arquiva o backup com um nome novo depois de restaurar o conteúdo.
     */
    fun rollbackRepair(workspace: File, repair: EspelharRepair): File {
        require(repair.path.isNotBlank() && !repair.path.startsWith('/') && !repair.path.contains('\\') &&
            repair.path.split('/').none { it.isBlank() || it == "." || it == ".." || it.startsWith(".") }) {
            "Caminho de reversão inválido."
        }
        val root = workspace.canonicalFile
        val target = File(root, repair.path).canonicalFile
        require(target.path.startsWith(root.path + File.separator) && target.isFile) {
            "Ficheiro fora do workspace ou inexistente."
        }
        val backup = File(target.parentFile, target.name + ".hubops-backup")
        require(backup.isFile) { "Cópia de segurança não encontrada; a reversão foi cancelada." }
        val current = target.readText(Charsets.UTF_8)
        require(sha256(current) == sha256(repair.replacement)) {
            "O ficheiro foi alterado depois da reparação; não será sobrescrito pela reversão."
        }
        val original = backup.readText(Charsets.UTF_8)
        require(sha256(original) == repair.beforeHash) {
            "A cópia de segurança não corresponde ao conteúdo original; reversão recusada."
        }
        val restoreTemp = File(target.parentFile, ".${target.name}.${System.nanoTime()}.rollback.tmp")
        try {
            restoreTemp.writeText(original, Charsets.UTF_8)
            moveReplacing(restoreTemp, target)
            require(sha256(target.readText(Charsets.UTF_8)) == repair.beforeHash) {
                "A verificação do conteúdo restaurado falhou."
            }
        } finally {
            restoreTemp.delete()
        }
        val archivedBackup = File(target.parentFile, target.name + ".hubops-backup-restored-" + System.currentTimeMillis())
        runCatching { moveWithoutReplace(backup, archivedBackup) }
        return if (archivedBackup.isFile) archivedBackup else backup
    }

    /** Aplica uma proposta somente se o conteúdo atual ainda corresponder ao hash analisado. */
    fun applyRepair(workspace: File, repair: EspelharRepair): File {
        require(repair.path.isNotBlank() && !repair.path.startsWith('/') && !repair.path.contains('\\') &&
            repair.path.split('/').none { it.isBlank() || it == "." || it == ".." || it.startsWith(".") }) {
            "Caminho de reparação inválido."
        }
        require(repair.replacement.length <= 1_000_000) { "A reparação excede 1 MB." }
        val root = workspace.canonicalFile
        val target = File(root, repair.path).canonicalFile
        require(target.path.startsWith(root.path + File.separator) && target.isFile) { "Ficheiro fora do workspace ou inexistente." }
        val original = target.readText(Charsets.UTF_8)
        require(sha256(original) == repair.beforeHash) { "O ficheiro mudou desde a análise; gere novamente as propostas." }
        val normalized = original.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
            .lineSequence().joinToString("\n") { it.trimEnd(' ', '\t') }
        val expected = when (repair.id) {
            "remove-utf8-bom" -> original.removePrefix("\uFEFF").takeIf { original.startsWith("\uFEFF") }
            "normalize-whitespace" -> normalized.takeIf { normalized != original }
            "final-newline" -> (normalized + "\n").takeIf { original.isNotEmpty() && !original.endsWith('\n') }
            else -> null
        }
        require(expected != null && expected == repair.replacement) { "Proposta desconhecida ou conteúdo de reparação não corresponde à transformação permitida." }
        val backup = File(target.parentFile, target.name + ".hubops-backup")
        require(!backup.exists()) { "Já existe uma cópia de segurança; não será sobrescrita." }
        val backupTemp = File(target.parentFile, ".${target.name}.${System.nanoTime()}.backup.tmp")
        val replacementTemp = File(target.parentFile, ".${target.name}.${System.nanoTime()}.repair.tmp")
        try {
            backupTemp.writeText(original, Charsets.UTF_8)
            replacementTemp.writeText(repair.replacement, Charsets.UTF_8)
            // Publica primeiro a cópia original; só depois substitui o ficheiro de trabalho.
            moveWithoutReplace(backupTemp, backup)
            moveReplacing(replacementTemp, target)
        } catch (failure: Exception) {
            if (backup.exists() && target.readText(Charsets.UTF_8) != original) {
                runCatching { moveReplacing(File(backup.path), target) }
            }
            throw failure
        } finally {
            backupTemp.delete()
            replacementTemp.delete()
        }
        return backup
    }

    private fun moveWithoutReplace(source: File, target: File) {
        try { java.nio.file.Files.move(source.toPath(), target.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE) }
        catch (_: java.nio.file.AtomicMoveNotSupportedException) { java.nio.file.Files.move(source.toPath(), target.toPath()) }
    }

    private fun moveReplacing(source: File, target: File) {
        try { java.nio.file.Files.move(source.toPath(), target.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
        catch (_: java.nio.file.AtomicMoveNotSupportedException) { java.nio.file.Files.move(source.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
    }

    private fun repair(path: String, id: String, title: String, detail: String, before: String, after: String) =
        EspelharRepair(id, path, title, detail, sha256(before), after)

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
