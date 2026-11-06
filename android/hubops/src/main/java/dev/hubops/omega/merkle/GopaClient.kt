package dev.hubops.omega.merkle

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** GoPA v2 client. HTTPS and bearer transport authentication are mandatory; signatures remain Ed25519. */
class GopaClient(private val serverUrl: String, private val transportToken: String) {
    init {
        require(serverUrl.startsWith("https://", ignoreCase = true)) { "GoPA exige HTTPS; HTTP não é permitido." }
        require(transportToken.length >= 32) { "Token de transporte deve ter pelo menos 32 caracteres." }
    }
    data class RemoteConsensus(val artifactHash: String, val finalVerdict: String,
        val requiredQuorum: Int, val totalEligiblePeers: Int, val totalValid: Int,
        val agreeingPeers: Int, val accepted: Boolean, val dissenters: List<String>, val reason: String)

    suspend fun postAttestation(att: Attestation): Result<Unit> = withContext(Dispatchers.IO) { runCatching {
        val body = JSONObject().apply {
            put("artifactHash", att.artifactHash); put("verdict", att.verdict.name)
            put("evidenceHashes", JSONArray(att.evidenceHashes)); put("evidenceRoot", att.evidenceRoot)
            put("issuedAtEpochMs", att.issuedAtEpochMs); put("peerId", att.peerId)
            put("signatureBase64", att.signatureBase64)
        }.toString()
        request("POST", "/attest", body).useConnection { c ->
            if (c.responseCode !in 200..299) error("GoPA HTTP ${c.responseCode}: ${c.errorStream?.bufferedReader()?.use { it.readText() }}")
        }
    } }

    suspend fun getConsensus(artifactHash: String): Result<RemoteConsensus> = withContext(Dispatchers.IO) { runCatching {
        require(artifactHash.matches(Regex("(?i)[a-f0-9]{64}")))
        request("GET", "/consensus/${artifactHash.lowercase()}").useConnection { c ->
            if (c.responseCode !in 200..299) error("GoPA HTTP ${c.responseCode}")
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            RemoteConsensus(o.getString("artifactHash"), o.getString("finalVerdict"),
                o.getInt("requiredQuorum"), o.getInt("totalEligiblePeers"), o.getInt("totalValid"),
                o.getInt("agreeingPeers"), o.getBoolean("accepted"), o.optJSONArray("dissenters").toStrings(),
                o.optString("reason"))
        }
    } }

    suspend fun gossip(knownPeerArtifactIds: Set<String>): Result<List<Attestation>> = withContext(Dispatchers.IO) { runCatching {
        val query = java.net.URLEncoder.encode(knownPeerArtifactIds.joinToString(","), Charsets.UTF_8.name())
        request("GET", "/gossip/delta?known=$query").useConnection { c ->
            if (c.responseCode !in 200..299) error("GoPA HTTP ${c.responseCode}")
            val arr = JSONArray(c.inputStream.bufferedReader().use { it.readText() })
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Attestation(o.getString("artifactHash"), AuditVerdict.valueOf(o.getString("verdict")),
                    o.getJSONArray("evidenceHashes").toStrings(), o.getString("evidenceRoot"),
                    o.getLong("issuedAtEpochMs"), o.getString("peerId"), o.getString("signatureBase64"))
            }
        }
    } }

    private inline fun <T> HttpURLConnection.useConnection(block: (HttpURLConnection) -> T): T = try { block(this) } finally { disconnect() }

    private fun request(method: String, path: String, body: String? = null): HttpURLConnection {
        val c = URL(serverUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        c.requestMethod = method; c.connectTimeout = 5_000; c.readTimeout = 10_000
        c.setRequestProperty("Authorization", "Bearer $transportToken")
        c.setRequestProperty("Accept", "application/json")
        if (body != null) { c.doOutput = true; c.setRequestProperty("Content-Type", "application/json; charset=utf-8"); c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) } }
        return c
    }
    private fun JSONArray?.toStrings(): List<String> = this?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
}
