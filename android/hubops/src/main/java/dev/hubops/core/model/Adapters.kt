package dev.hubops.core.model

import org.json.JSONArray
import org.json.JSONObject

/** Parsers de JSON (sem dependência de android.*, testáveis na JVM). */
object Parsers {

    private fun JSONObject.str(key: String): String =
        if (isNull(key)) "" else optString(key, "")

    fun repos(json: String): List<RepoDto> {
        val text = json.trim()
        if (text.isEmpty()) return emptyList()
        val arr: JSONArray = if (text.startsWith("{")) {
            JSONObject(text).optJSONArray("items") ?: JSONArray()
        } else {
            JSONArray(text)
        }
        val out = ArrayList<RepoDto>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.str("full_name")
            if (name.isBlank()) continue
            out += RepoDto(
                fullName = name,
                description = o.str("description"),
                stars = o.optInt("stargazers_count", 0).coerceAtLeast(0),
                forks = o.optInt("forks_count", 0).coerceAtLeast(0),
                language = o.str("language"),
                updatedAt = o.str("updated_at"),
                isPrivate = o.optBoolean("private", false),
                url = o.str("html_url"),
            )
        }
        return out
    }

    fun categories(json: String): List<CategoryDto> {
        val text = json.trim()
        if (text.isEmpty()) return emptyList()
        val arr = JSONArray(text)
        val out = ArrayList<CategoryDto>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val id = item.optLong("id", 0L)
            val name = stripHtml(item.str("name")).trim()
            if (id > 0L && name.isNotBlank()) {
                out += CategoryDto(id, name, item.optInt("count", 0).coerceAtLeast(0), item.str("slug"))
            }
        }
        return out
    }

    fun posts(json: String): List<PostDto> {
        val text = json.trim()
        if (text.isEmpty()) return emptyList()
        val arr = JSONArray(text)
        val out = ArrayList<PostDto>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optLong("id", -1L)
            if (id <= 0) continue
            out += PostDto(
                id = id,
                title = stripHtml(o.optJSONObject("title")?.str("rendered") ?: ""),
                excerpt = stripHtml(o.optJSONObject("excerpt")?.str("rendered") ?: ""),
                link = o.str("link"),
                date = o.str("date"),
            )
        }
        return out
    }

    /** Extrai texto legível de títulos/excertos WordPress e descodifica entidades comuns/numéricas. */
    fun stripHtml(html: String): String {
        val withoutExecutableBlocks = html
            .replace(Regex("(?is)<(script|style)\\b[^>]*>.*?</\\1\\s*>"), " ")
            .replace(Regex("(?s)<!--.*?-->"), " ")
            .replace(Regex("<[^>]*>"), " ")
        val named = withoutExecutableBlocks
            .replace("&amp;", "&", true)
            .replace("&lt;", "<", true)
            .replace("&gt;", ">", true)
            .replace("&quot;", "\"", true)
            .replace("&apos;", "'", true)
            .replace("&#8217;", "’")
            .replace("&#039;", "'")
            .replace("&nbsp;", " ", true)
            .replace("&hellip;", "…", true)
            .replace("&ndash;", "–", true)
            .replace("&mdash;", "—", true)
            .replace("&lsquo;", "‘", true)
            .replace("&rsquo;", "’", true)
            .replace("&ldquo;", "“", true)
            .replace("&rdquo;", "”", true)
            .replace("&aacute;", "á", true)
            .replace("&eacute;", "é", true)
            .replace("&iacute;", "í", true)
            .replace("&oacute;", "ó", true)
            .replace("&uacute;", "ú", true)
            .replace("&atilde;", "ã", true)
            .replace("&otilde;", "õ", true)
            .replace("&ccedil;", "ç", true)
            .replace("&Aacute;", "Á", true)
            .replace("&Eacute;", "É", true)
            .replace("&Oacute;", "Ó", true)
            .replace("&Uacute;", "Ú", true)
            .replace("&Atilde;", "Ã", true)
            .replace("&Otilde;", "Õ", true)
            .replace("&Ccedil;", "Ç", true)
        val decoded = Regex("&#(x[0-9a-fA-F]{1,6}|[0-9]{1,7});").replace(named) { match ->
            val token = match.groupValues[1]
            val codePoint = try {
                if (token.startsWith("x", true)) token.substring(1).toInt(16) else token.toInt(10)
            } catch (_: NumberFormatException) { -1 }
            if (codePoint in 1..0x10FFFF && Character.isValidCodePoint(codePoint) && codePoint !in 0xD800..0xDFFF)
                String(Character.toChars(codePoint)) else " "
        }
        return decoded.replace(Regex("\\s+"), " ").trim()
    }
}

// ---- Adapters: cada fonte -> FeedItem ----

fun RepoDto.toFeedItem(): FeedItem {
    val lang = if (language.isBlank()) "" else " \u00b7 $language"
    return FeedItem(Source.GITHUB, fullName, "\u2605 $stars$lang", url.ifBlank { null })
}

fun PostDto.toFeedItem(): FeedItem =
    FeedItem(Source.WORDPRESS, title.ifBlank { "(sem título)" }, date, link.ifBlank { null })

fun CommandResult.toFeedItem(): FeedItem {
    val status = when {
        timedOut -> "tempo esgotado"
        else -> "código $exitCode"
    }
    return FeedItem(Source.TERMINAL, "Terminal · comando executado", status, null)
}
