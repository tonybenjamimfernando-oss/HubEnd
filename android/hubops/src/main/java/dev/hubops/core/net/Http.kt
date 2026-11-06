package dev.hubops.core.net

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

class HttpFailure(val code: Int, message: String) : Exception(message)

/** Cliente HTTP com HTTPS obrigatório, redirecionamentos controlados e limites de recursos. */
object Http {
    private const val MAX_REDIRECTS = 3
    private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

    /** Caminho legado mantido sem alteração para preservar os módulos existentes. */
    fun request(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        timeoutMs: Int = 15_000,
        maxBytes: Int = 2_000_000,
    ): String {
        require(url.startsWith("https://")) { "Apenas URLs https:// são permitidas." }
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = true
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val text = stream?.use { readCapped(it, maxBytes) } ?: ""
            if (code >= 400) throw HttpFailure(code, "HTTP $code: ${text.take(200)}")
            return text
        } finally {
            conn.disconnect()
        }
    }

    /** Variante estrita usada pelos módulos gerais novos; não altera o comportamento do legado. */
    fun requestSafe(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        timeoutMs: Int = 15_000,
        maxBytes: Int = 2_000_000,
    ): String {
        require(timeoutMs in 1_000..120_000) { "Tempo limite HTTP inválido." }
        require(maxBytes in 1..20_000_000) { "Limite de resposta inválido." }
        require(method.matches(Regex("[A-Z]{3,10}"))) { "Método HTTP inválido." }

        var current = validateHttpsUrl(url)
        var currentMethod = method
        var currentBody = body
        var currentHeaders = headers.toMutableMap()

        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val conn = (URL(current.toString()).openConnection() as HttpURLConnection)
            try {
                conn.requestMethod = currentMethod
                conn.connectTimeout = timeoutMs
                conn.readTimeout = timeoutMs
                conn.instanceFollowRedirects = false
                conn.useCaches = false
                currentHeaders.forEach { (key, value) ->
                    require(key.isNotBlank() && !key.contains('\n') && !key.contains('\r')) { "Cabeçalho HTTP inválido." }
                    require(!value.contains('\n') && !value.contains('\r')) { "Valor de cabeçalho inválido." }
                    conn.setRequestProperty(key, value)
                }
                val bodyBytes = currentBody?.toByteArray(Charsets.UTF_8)
                if (bodyBytes != null && currentMethod !in setOf("GET", "HEAD")) {
                    conn.doOutput = true
                    conn.setFixedLengthStreamingMode(bodyBytes.size)
                    conn.outputStream.use { it.write(bodyBytes) }
                }

                val code = conn.responseCode
                if (code in REDIRECT_CODES) {
                    val location = conn.getHeaderField("Location")
                        ?: throw HttpFailure(code, "Redirecionamento HTTP sem destino.")
                    if (redirectCount >= MAX_REDIRECTS) throw HttpFailure(code, "Demasiados redirecionamentos HTTP.")
                    if (currentMethod !in setOf("GET", "HEAD")) {
                        throw HttpFailure(code, "Redirecionamento de escrita bloqueado para evitar repetição ou alteração do pedido.")
                    }
                    val next = validateHttpsUrl(current.resolve(location).toString())
                    if (!sameOrigin(current, next)) {
                        currentHeaders.keys.removeAll { it.equals("Authorization", true) || it.equals("Cookie", true) || it.equals("Proxy-Authorization", true) }
                    }
                    current = next
                    currentBody = null
                    return@repeat
                }

                val stream = if (code >= 400) conn.errorStream else conn.inputStream
                val text = stream?.use { readCapped(it, maxBytes) } ?: ""
                if (code >= 400) throw HttpFailure(code, "HTTP $code: ${text.take(200)}")
                return text
            } finally {
                conn.disconnect()
            }
        }
        error("Não foi possível concluir o pedido HTTP.")
    }

    internal fun validateHttpsUrl(raw: String): URI {
        val uri = try { URI(raw).normalize() } catch (_: Exception) { throw IllegalArgumentException("URL inválida.") }
        require(uri.scheme.equals("https", true)) { "Apenas URLs https:// são permitidas." }
        require(!uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null) { "URL insegura ou inválida." }
        require(uri.port == -1 || uri.port in 1..65535) { "Porta inválida." }
        require(!uri.host.contains('%')) { "Host inválido." }
        return uri
    }

    private fun sameOrigin(a: URI, b: URI): Boolean =
        a.host.equals(b.host, true) && effectivePort(a) == effectivePort(b)

    private fun effectivePort(uri: URI): Int = if (uri.port == -1) 443 else uri.port

    private fun readCapped(input: InputStream, maxBytes: Int): String {
        val out = ByteArrayOutputStream(minOf(maxBytes, 8192))
        val buf = ByteArray(8192)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            if (out.size() + n > maxBytes) throw IllegalStateException("Resposta demasiado grande (limite ${maxBytes} bytes).")
            out.write(buf, 0, n)
        }
        return out.toString("UTF-8")
    }
}
