package dev.hubops.despertar

import dev.hubops.core.config.SecureConfig
import dev.hubops.core.net.Http
import dev.hubops.core.net.HttpFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Base64

data class RepoAccess(val fullName: String, val defaultBranch: String, val canPush: Boolean)

/**
 * Despertar: só age em repositórios sobre os quais o token do utilizador tem permissão de escrita.
 * Repositórios de terceiros são apenas LIDOS (API pública) para estudo.
 */
class DespertarService(private val config: SecureConfig) {

    private val api = "https://api.github.com"

    suspend fun checkAccess(owner: String, repo: String): RepoAccess = withContext(Dispatchers.IO) {
        requireName(owner, repo)
        val o = JSONObject(Http.request("$api/repos/$owner/$repo", headers = headers()))
        RepoAccess(
            fullName = o.optString("full_name"),
            defaultBranch = o.optString("default_branch", "main"),
            canPush = o.optJSONObject("permissions")?.optBoolean("push", false) ?: false,
        )
    }

    /** Recolhe metadados, linguagens, tópicos e README de um repositório público para estudo. */
    suspend fun inspectRepository(owner: String, repo: String): RepositoryStudy = withContext(Dispatchers.IO) {
        requireName(owner, repo)
        val root = "$api/repos/$owner/$repo"
        val metadata = JSONObject(Http.request(root, headers = headers()))
        val licenseObj = metadata.optJSONObject("license")
        val spdx = licenseObj?.optString("spdx_id", "")?.takeUnless { it == "NOASSERTION" }.orEmpty()
        val languagesJson = JSONObject(Http.request("$root/languages", headers = headers()))
        val languages = buildMap {
            val keys = languagesJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, languagesJson.optLong(key, 0L))
            }
        }
        val topics = metadata.optJSONArray("topics")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
        }.orEmpty()
        val readme = try {
            val readmeJson = JSONObject(Http.request("$root/readme", headers = headers() + ("Accept" to "application/vnd.github+json"), maxBytes = 1_000_000))
            val encoded = readmeJson.optString("content").replace("\n", "")
            if (encoded.isBlank()) "" else String(Base64.getMimeDecoder().decode(encoded), Charsets.UTF_8)
                .lineSequence().take(35).joinToString("\n").take(5000)
        } catch (_: Exception) { "" }

        // Auditoria estrutural baseada na árvore GitHub (sem clonar nem descarregar o código-fonte).
        val tree = try {
            JSONObject(Http.request(
                "$root/git/trees/${URLEncoder.encode(metadata.optString("default_branch", "main"), "UTF-8")}?recursive=1",
                headers = headers(), maxBytes = 2_000_000,
            ))
        } catch (_: Exception) { JSONObject() }
        val treeItems = tree.optJSONArray("tree") ?: JSONArray()
        val paths = (0 until treeItems.length()).mapNotNull { treeItems.optJSONObject(it)?.optString("path")?.takeIf(String::isNotBlank) }
        val sourceExtensions = setOf("kt", "kts", "java", "js", "jsx", "ts", "tsx", "py", "go", "rs", "swift", "dart", "c", "cpp", "h", "cs")
        val sourceCount = paths.count { path -> path.substringAfterLast('.', "").lowercase() in sourceExtensions }
        val testCount = paths.count { path ->
            val lower = path.lowercase()
            lower.contains("/test/") || lower.startsWith("test/") || lower.contains("tests/") ||
                lower.endsWith("test.kt") || lower.endsWith("test.java") || lower.endsWith("_test.go")
        }
        val workflowCount = paths.count { it.startsWith(".github/workflows/") && (it.endsWith(".yml") || it.endsWith(".yaml")) }
        val rootFiles = paths.filter { '/' !in it }.take(80)
        val dependencyNames = setOf("package.json", "package-lock.json", "pnpm-lock.yaml", "yarn.lock", "pom.xml", "build.gradle", "build.gradle.kts", "gradle/libs.versions.toml", "requirements.txt", "pyproject.toml", "poetry.lock", "Pipfile", "Cargo.toml", "Cargo.lock", "go.mod", "go.sum", "pubspec.yaml", "composer.json", "Gemfile", "Podfile")
        val dependencyManifests = paths.filter { it.substringAfterLast('/') in dependencyNames }.take(40)
        val lowerPaths = paths.map { it.lowercase() }.toSet()
        val buildSystems = buildList {
            if (lowerPaths.any { it.endsWith("build.gradle") || it.endsWith("build.gradle.kts") || it.endsWith("settings.gradle") || it.endsWith("settings.gradle.kts") }) add("Gradle")
            if ("pom.xml" in lowerPaths) add("Maven")
            if ("package.json" in lowerPaths) add("Node.js")
            if ("pyproject.toml" in lowerPaths || "requirements.txt" in lowerPaths || "pipfile" in lowerPaths) add("Python")
            if ("cargo.toml" in lowerPaths) add("Cargo/Rust")
            if ("go.mod" in lowerPaths) add("Go Modules")
            if ("pubspec.yaml" in lowerPaths) add("Flutter/Dart")
            if ("composer.json" in lowerPaths) add("Composer/PHP")
        }
        val recentCommits = try {
            val commits = JSONArray(Http.request("$root/commits?per_page=5", headers = headers(), maxBytes = 500_000))
            (0 until commits.length()).mapNotNull { i ->
                commits.optJSONObject(i)?.let { c ->
                    val message = c.optJSONObject("commit")?.optString("message", "")?.lineSequence()?.firstOrNull().orEmpty().take(120)
                    val date = c.optJSONObject("commit")?.optJSONObject("author")?.optString("date", "").orEmpty().take(10)
                    if (message.isBlank()) null else "$date — $message"
                }
            }
        } catch (_: Exception) { emptyList() }
        val latestRelease = try {
            val releases = JSONArray(Http.request("$root/releases?per_page=1", headers = headers(), maxBytes = 250_000))
            releases.optJSONObject(0)?.let { r ->
                "${r.optString("tag_name", "")}${r.optString("name").takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()}"
            }.orEmpty()
        } catch (_: Exception) { "" }
        val securityPolicy = paths.any { it.equals("SECURITY.md", true) || it.equals(".github/SECURITY.md", true) }
        val reviewCandidates = (0 until treeItems.length()).mapNotNull { index ->
            val obj = treeItems.optJSONObject(index) ?: return@mapNotNull null
            val path = obj.optString("path")
            val extension = path.substringAfterLast('.', "").lowercase()
            val isManifest = path.substringAfterLast('/').equals("AndroidManifest.xml", true) ||
                path.substringAfterLast('/') in setOf("build.gradle", "build.gradle.kts", "package.json", "pom.xml", "pyproject.toml")
            val isSource = extension in setOf("kt", "java", "js", "jsx", "ts", "tsx", "py", "go", "rs", "xml")
            val size = obj.optLong("size", Long.MAX_VALUE)
            val sha = obj.optString("sha")
            if ((isManifest || isSource) && size in 1..80_000 && Regex("^[a-fA-F0-9]{40}$").matches(sha)) {
                Triple(path, sha, size)
            } else null
        }.sortedBy { (path, _, _) ->
            when {
                path.substringAfterLast('/').equals("AndroidManifest.xml", true) -> 0
                path.contains("/security/", true) || path.contains("auth", true) -> 1
                path.contains("network", true) || path.contains("api", true) -> 2
                path.substringAfterLast('/') in setOf("build.gradle", "build.gradle.kts", "package.json", "pom.xml", "pyproject.toml") -> 3
                else -> 4
            }
        }.take(8)
        val reviewedFiles = linkedMapOf<String, String>()
        for ((path, sha, _) in reviewCandidates) {
            try {
                val blob = JSONObject(Http.request("$root/git/blobs/$sha", headers = headers(), maxBytes = 150_000))
                if (blob.optString("encoding") == "base64") {
                    val content = Base64.getMimeDecoder().decode(blob.optString("content").replace("\n", ""))
                    reviewedFiles[path] = String(content, Charsets.UTF_8).take(80_000)
                }
            } catch (_: Exception) { /* Ficheiro grande/indisponível: continua com os restantes. */ }
        }
        val codeAudit = StaticCodeAudit.analyze(reviewedFiles, paths)
        RepositoryStudy(
            fullName = metadata.optString("full_name", "$owner/$repo"),
            description = metadata.optString("description", ""),
            url = metadata.optString("html_url", "https://github.com/$owner/$repo"),
            defaultBranch = metadata.optString("default_branch", "main"),
            language = metadata.optString("language", ""),
            license = spdx,
            licenseClass = LicensePolicy.classify(spdx),
            stars = metadata.optInt("stargazers_count"),
            forks = metadata.optInt("forks_count"),
            openIssues = metadata.optInt("open_issues_count"),
            archived = metadata.optBoolean("archived"),
            updatedAt = metadata.optString("updated_at", ""),
            topics = topics,
            languages = languages,
            readmeExcerpt = readme,
            rootFiles = rootFiles,
            buildSystems = buildSystems,
            dependencyManifests = dependencyManifests,
            filesReviewed = codeAudit.filesReviewed,
            codeFindings = codeAudit.findings,
            recentCommits = recentCommits,
            latestRelease = latestRelease,
            sourceFileCount = sourceCount,
            testFileCount = testCount,
            workflowCount = workflowCount,
            securityPolicyFound = securityPolicy,
            treeAvailable = tree.has("tree"),
            treeTruncated = tree.optBoolean("truncated", false),
        )
    }

    suspend fun findInspirations(owner: String, repo: String, keywords: String): List<SimilarRepo> =
        withContext(Dispatchers.IO) {
            requireName(owner, repo)
            val kw = keywords.trim()
            require(kw.length in 2..80) { "Palavras-chave: 2 a 80 caracteres." }
            val url = "$api/search/repositories?per_page=10&sort=stars&q=" + URLEncoder.encode(kw, "UTF-8")
            val items = JSONObject(Http.request(url, headers = headers())).optJSONArray("items") ?: JSONArray()
            val self = "$owner/$repo".lowercase()
            val out = ArrayList<SimilarRepo>()
            for (i in 0 until items.length()) {
                val it = items.optJSONObject(i) ?: continue
                val name = it.optString("full_name")
                if (name.isBlank() || name.lowercase() == self || it.optBoolean("private", false)) continue
                val spdx = it.optJSONObject("license")?.optString("spdx_id", "") ?: ""
                out += SimilarRepo(
                    fullName = name,
                    url = it.optString("html_url"),
                    spdx = if (spdx == "NOASSERTION") "" else spdx,
                    licenseClass = LicensePolicy.classify(spdx),
                    stars = it.optInt("stargazers_count", 0),
                )
            }
            out
        }

    /**
     * Cria o ramo despertar/atribuicoes e grava ATTRIBUTIONS.md nele (nunca toca no ramo principal).
     * Devolve o nome do ramo.
     */
    suspend fun commitLedger(owner: String, repo: String, markdown: String): String =
        withContext(Dispatchers.IO) {
            val access = checkAccess(owner, repo)
            require(access.canPush) { "O token não tem permissão de escrita neste repositório." }
            val branch = "despertar/atribuicoes"
            val base = JSONObject(
                Http.request("$api/repos/$owner/$repo/git/ref/heads/${access.defaultBranch}", headers = headers()),
            ).getJSONObject("object").getString("sha")

            try {
                Http.request(
                    "$api/repos/$owner/$repo/git/refs", "POST", jsonHeaders(),
                    JSONObject().put("ref", "refs/heads/$branch").put("sha", base).toString(),
                )
            } catch (e: HttpFailure) {
                if (e.code != 422) throw e // 422 = o ramo já existe
            }

            val path = "$api/repos/$owner/$repo/contents/ATTRIBUTIONS.md"
            val existingSha = try {
                JSONObject(Http.request("$path?ref=$branch", headers = headers())).optString("sha", "")
            } catch (e: HttpFailure) {
                if (e.code == 404) "" else throw e
            }
            val body = JSONObject()
                .put("message", "docs: atribuições geradas pelo HubOps Despertar")
                .put("content", Base64.getEncoder().encodeToString(markdown.toByteArray(Charsets.UTF_8)))
                .put("branch", branch)
            if (existingSha.isNotBlank()) body.put("sha", existingSha)
            Http.request(path, "PUT", jsonHeaders(), body.toString())
            branch
        }

    private fun requireName(owner: String, repo: String) {
        require(NAME.matches(owner)) { "Dono inválido." }
        require(REPO.matches(repo)) { "Repositório inválido." }
    }

    private fun headers(): Map<String, String> {
        val h = mutableMapOf(
            "Accept" to "application/vnd.github+json",
            "X-GitHub-Api-Version" to "2022-11-28",
            "User-Agent" to "HubOps/1.0",
        )
        val t = config.githubToken
        if (t.isNotBlank()) h["Authorization"] = "Bearer $t"
        return h
    }

    private fun jsonHeaders(): Map<String, String> = headers() + ("Content-Type" to "application/json")

    companion object {
        val NAME = Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$")
        val REPO = Regex("^[A-Za-z0-9._-]{1,100}$")
    }
}
