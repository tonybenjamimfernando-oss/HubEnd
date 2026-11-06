package dev.hubops.data.github

import dev.hubops.core.config.SecureConfig
import dev.hubops.core.model.Parsers
import dev.hubops.core.model.RepoDto
import dev.hubops.core.model.RepoResource
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import dev.hubops.core.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/** Cliente GitHub REST com paginação explícita e validação de entrada. */
class GitHubService(private val config: SecureConfig) {

    suspend fun searchRepos(query: String, page: Int = 1, perPage: Int = 20): List<RepoDto> =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            require(q.length in 2..100) { "A pesquisa deve ter entre 2 e 100 caracteres." }
            validatePage(page, perPage)
            val url = "https://api.github.com/search/repositories?per_page=$perPage&page=$page&q=" +
                URLEncoder.encode(q, "UTF-8")
            Parsers.repos(Http.requestSafe(url, headers = headers(), maxBytes = 2_000_000))
        }

    suspend fun userRepos(user: String, page: Int = 1, perPage: Int = 30): List<RepoDto> =
        withContext(Dispatchers.IO) {
            val u = user.trim()
            require(isValidUser(u)) { "Nome de utilizador GitHub inválido." }
            validatePage(page, perPage)
            val url = "https://api.github.com/users/$u/repos?per_page=$perPage&page=$page&sort=updated"
            Parsers.repos(Http.requestSafe(url, headers = headers(), maxBytes = 2_000_000))
        }

    /** Repositórios associados ao token, incluindo privados conforme os scopes/permissões concedidos. */
    suspend fun myRepos(page: Int = 1, perPage: Int = 30): List<RepoDto> = withContext(Dispatchers.IO) {
        validatePage(page, perPage)
        require(config.githubToken.isNotBlank()) { "Configure um token GitHub em Config para consultar os seus repositórios." }
        val url = "https://api.github.com/user/repos?per_page=$perPage&page=$page&sort=updated&direction=desc&affiliation=owner,collaborator,organization"
        Parsers.repos(Http.requestSafe(url, headers = headers(), maxBytes = 2_000_000))
    }

    /** Importa snapshot somente-leitura de fontes de um repositório para análise local; nunca faz push. */
    suspend fun sourceSnapshot(fullName: String, ref: String = ""): Map<String, String> = withContext(Dispatchers.IO) {
        require(isValidRepository(fullName)) { "Identificador de repositório inválido." }
        val parts = fullName.trim().split('/')
        val repoJson = JSONObject(Http.requestSafe("https://api.github.com/repos/${parts[0]}/${parts[1]}", headers = headers(), maxBytes = 1_000_000))
        val branch = ref.trim().ifBlank { repoJson.optString("default_branch") }
        require(branch.length in 1..200 && !branch.startsWith('-') && !branch.contains('\n')) { "Branch/ref inválida." }
        val treeUrl = "https://api.github.com/repos/${parts[0]}/${parts[1]}/git/trees/${URLEncoder.encode(branch, "UTF-8")}?recursive=1"
        val tree = JSONObject(Http.requestSafe(treeUrl, headers = headers(), maxBytes = 3_000_000))
        require(!tree.optBoolean("truncated")) { "A árvore do repositório foi truncada pelo GitHub; reduza o escopo ou use uma branch menor." }
        val entries = tree.optJSONArray("tree") ?: JSONArray()
        val allowed = setOf("kt", "kts", "java", "py", "js", "ts", "json", "xml", "sh", "md", "gradle", "properties", "yml", "yaml")
        val candidates = (0 until entries.length()).mapNotNull { entries.optJSONObject(it) }
            .filter { it.optString("type") == "blob" && it.optLong("size", Long.MAX_VALUE) in 1..500_000 &&
                it.optString("path").split('/').none { segment -> segment.startsWith(".") || segment.isBlank() || segment == ".." } &&
                it.optString("path").substringAfterLast('.', "").lowercase() in allowed }
            .sortedBy { it.optString("path") }
        require(candidates.size <= 40) { "O snapshot excede 40 ficheiros suportados; selecione uma branch/projeto menor para respeitar os limites da API." }
        val result = linkedMapOf<String, String>()
        var totalBytes = 0L
        candidates.forEach { item ->
            val path = item.optString("path")
            require(!path.startsWith('/') && path.split('/').none { it.isBlank() || it == "." || it == ".." }) { "O repositório contém caminho inválido." }
            val sha = item.optString("sha")
            require(sha.matches(Regex("^[a-fA-F0-9]{40}$"))) { "Identificador de blob inválido." }
            val blob = JSONObject(Http.requestSafe("https://api.github.com/repos/${parts[0]}/${parts[1]}/git/blobs/$sha", headers = headers(), maxBytes = 800_000))
            require(blob.optString("encoding") == "base64") { "Codificação de blob não suportada: $path" }
            val bytes = Base64.getMimeDecoder().decode(blob.optString("content"))
            require(bytes.size <= 500_000) { "Ficheiro excede 500 KB: $path" }
            totalBytes += bytes.size
            require(totalBytes <= 10L * 1024 * 1024) { "Snapshot excede 10 MB descomprimidos." }
            val text = bytes.toString(Charsets.UTF_8)
            require(!text.contains('\u0000')) { "Ficheiro binário rejeitado: $path" }
            result[path] = text
        }
        result
    }

    /** Explora um recurso de um repositório sem executar nem descarregar o código. */
    suspend fun repoResources(fullName: String, kind: String, page: Int = 1, perPage: Int = 20): List<RepoResource> =
        withContext(Dispatchers.IO) {
            require(isValidRepository(fullName)) { "Identificador de repositório inválido." }
            require(isValidResourceKind(kind)) { "Tipo de recurso GitHub inválido." }
            val parts = fullName.trim().split('/')
            validatePage(page, perPage)
            val url = "https://api.github.com/repos/${parts[0]}/${parts[1]}/$kind?per_page=$perPage&page=$page"
            val json = Http.requestSafe(url, headers = headers(), maxBytes = 2_000_000)
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val (title, subtitle) = when (kind) {
                        "branches" -> item.optString("name") to "Commit ${item.optJSONObject("commit")?.optString("sha", "")?.take(12).orEmpty()} · ${if (item.optBoolean("protected")) "protegido" else "não protegido"}"
                        "commits" -> {
                            val commit = item.optJSONObject("commit")
                            val author = commit?.optJSONObject("author")
                            item.optString("sha").take(12) to "${commit?.optString("message", "")?.lineSequence()?.firstOrNull().orEmpty()} · ${author?.optString("date", "").orEmpty()}"
                        }
                        "releases" -> (item.optString("name").ifBlank { item.optString("tag_name") }) to "${item.optString("tag_name")} · ${item.optString("published_at")} · ${if (item.optBoolean("prerelease")) "pré-lançamento" else "lançamento"}"
                        else -> {
                            if (item.has("pull_request")) continue
                            "#${item.optInt("number")} ${item.optString("title")}" to "${item.optString("state")} · ${item.optString("updated_at")}"
                        }
                    }
                    val link = item.optString("html_url").takeIf { it.startsWith("https://github.com/") }.orEmpty()
                    if (title.isNotBlank()) add(RepoResource(kind, title, subtitle.take(500), link))
                }
            }
        }

    private fun validatePage(page: Int, perPage: Int) {
        require(page in 1..100) { "Página GitHub fora do intervalo permitido (1–100)." }
        require(perPage in 1..100) { "Quantidade por página inválida (1–100)." }
    }

    private fun headers(): Map<String, String> {
        val h = mutableMapOf(
            "Accept" to "application/vnd.github+json",
            "X-GitHub-Api-Version" to "2022-11-28",
            "User-Agent" to "HubOps/1.9.2",
        )
        val token = config.githubToken
        if (token.isNotBlank()) h["Authorization"] = "Bearer $token"
        return h
    }

    companion object {
        private val USER_REGEX = Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?$")
        private val REPO_REGEX = Regex("^[A-Za-z0-9_.-]{1,100}$")
        private val RESOURCE_KINDS = setOf("branches", "commits", "releases", "issues")
        fun isValidUser(user: String): Boolean = USER_REGEX.matches(user)
        fun isValidRepository(fullName: String): Boolean {
            val parts = fullName.trim().split('/')
            return parts.size == 2 && isValidUser(parts[0]) && REPO_REGEX.matches(parts[1]) &&
                parts[1] !in setOf(".", "..")
        }
        fun isValidResourceKind(kind: String): Boolean = kind in RESOURCE_KINDS
    }
}
