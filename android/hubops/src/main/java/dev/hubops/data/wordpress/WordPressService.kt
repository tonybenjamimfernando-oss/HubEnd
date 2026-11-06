package dev.hubops.data.wordpress

import dev.hubops.core.config.SecureConfig
import dev.hubops.core.model.Parsers
import dev.hubops.core.model.CategoryDto
import dev.hubops.core.model.PostDto
import dev.hubops.core.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Base64

/** Cliente WordPress REST: conteúdo público ou autorizado e criação explícita de rascunhos. */
class WordPressService(private val config: SecureConfig) {

    suspend fun latestPosts(siteInput: String, page: Int = 1, perPage: Int = 10): List<PostDto> =
        withContext(Dispatchers.IO) {
            require(page in 1..1000) { "Página WordPress fora do intervalo permitido." }
            require(perPage in 1..50) { "A página pode conter entre 1 e 50 posts." }
            val site = normalizeSite(siteInput)
            val authenticated = config.wpUser.isNotBlank() && config.wpAppPassword.isNotBlank()
            require(config.wpUser.isBlank() == config.wpAppPassword.isBlank()) {
                "Configure utilizador e Application Password WordPress em conjunto."
            }
            val status = if (authenticated) "any" else "publish"
            val url = "$site/wp-json/wp/v2/posts?status=$status&page=$page&per_page=$perPage&_fields=id,title,excerpt,link,date"
            Parsers.posts(Http.requestSafe(url, headers = requestHeaders(authenticated)))
        }

    suspend fun latestPages(siteInput: String, page: Int = 1, perPage: Int = 10): List<PostDto> =
        withContext(Dispatchers.IO) {
            require(page in 1..1000) { "Página WordPress fora do intervalo permitido." }
            require(perPage in 1..50) { "A página pode conter entre 1 e 50 páginas." }
            val site = normalizeSite(siteInput)
            val authenticated = config.wpUser.isNotBlank() && config.wpAppPassword.isNotBlank()
            require(config.wpUser.isBlank() == config.wpAppPassword.isBlank()) {
                "Configure utilizador e Application Password WordPress em conjunto."
            }
            val status = if (authenticated) "any" else "publish"
            val url = "$site/wp-json/wp/v2/pages?status=$status&page=$page&per_page=$perPage&_fields=id,title,excerpt,link,date"
            Parsers.posts(Http.requestSafe(url, headers = requestHeaders(authenticated)))
        }

    suspend fun categories(siteInput: String, page: Int = 1, perPage: Int = 50): List<CategoryDto> =
        withContext(Dispatchers.IO) {
            require(page in 1..1000 && perPage in 1..100) { "Paginação de categorias inválida." }
            val site = normalizeSite(siteInput)
            val json = Http.requestSafe(
                "$site/wp-json/wp/v2/categories?page=$page&per_page=$perPage&hide_empty=false&_fields=id,name,count,slug",
                headers = requestHeaders(authenticated = false),
                maxBytes = 1_000_000,
            )
            Parsers.categories(json)
        }

    /** Cria sempre um rascunho; nunca publica automaticamente. */
    suspend fun createDraft(siteInput: String, title: String, content: String): String =
        withContext(Dispatchers.IO) {
            val site = normalizeSite(siteInput)
            val t = title.trim()
            require(t.isNotEmpty() && t.length <= 200) { "Título obrigatório (máx. 200 caracteres)." }
            require(content.isNotBlank()) { "O conteúdo do rascunho não pode estar vazio." }
            require(content.length <= 50_000) { "Conteúdo demasiado grande (máx. 50.000 caracteres)." }
            require(config.wpUser.isNotBlank() && config.wpAppPassword.isNotBlank()) {
                "Configure utilizador e Application Password em Config."
            }
            val body = JSONObject().put("title", t).put("content", content).put("status", "draft").toString()
            val resp = Http.requestSafe(
                url = "$site/wp-json/wp/v2/posts",
                method = "POST",
                headers = requestHeaders(authenticated = true) + ("Content-Type" to "application/json; charset=utf-8"),
                body = body,
                maxBytes = 500_000,
            )
            val result = JSONObject(resp)
            require(result.optLong("id", 0L) > 0L && result.optString("status") == "draft") {
                "A API não confirmou a criação de um rascunho."
            }
            result.optString("link", "")
        }

    private fun requestHeaders(authenticated: Boolean): Map<String, String> {
        val headers = mutableMapOf("Accept" to "application/json", "User-Agent" to "HubOps/1.9.2")
        if (authenticated) {
            val user = config.wpUser
            val pass = config.wpAppPassword
            require(user.isNotBlank() && pass.isNotBlank()) {
                "Configure utilizador e Application Password WordPress em conjunto."
            }
            val auth = Base64.getEncoder().encodeToString("$user:$pass".toByteArray(Charsets.UTF_8))
            headers["Authorization"] = "Basic $auth"
        }
        return headers
    }

    companion object {
        fun normalizeSite(input: String): String {
            var raw = input.trim().trimEnd('/')
            require(raw.isNotEmpty()) { "Indique o endereço do site." }
            if (!raw.contains("://")) raw = "https://$raw"
            val uri = Http.validateHttpsUrl(raw)
            require(uri.rawQuery == null && uri.rawPath.orEmpty().let { !it.contains("/../") && !it.endsWith("/..") }) {
                "Indique apenas o endereço base do site, sem parâmetros ou caminhos relativos."
            }
            val path = uri.rawPath.orEmpty().trimEnd('/')
            return "https://${uri.rawAuthority}${path}"
        }
    }
}
