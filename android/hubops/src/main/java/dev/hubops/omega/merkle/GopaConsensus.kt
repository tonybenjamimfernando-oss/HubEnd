package dev.hubops.omega.merkle

import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.security.MessageDigest
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Provider
import java.util.Base64

/** Gossip + Proof-of-Audit. This is an attestation quorum, not a general BFT ledger. */
enum class AuditVerdict { PASS, FAIL, SUSPICIOUS }

data class PeerIdentity(val peerId: String, val publicKeyX509Base64: String, val enabled: Boolean = true)
data class Attestation(
    val artifactHash: String,
    val verdict: AuditVerdict,
    val evidenceHashes: List<String>,
    val evidenceRoot: String,
    val issuedAtEpochMs: Long,
    val peerId: String,
    val signatureBase64: String,
)
data class ConsensusResult(
    val artifactHash: String,
    val finalVerdict: String,
    val requiredQuorum: Int,
    val totalEligiblePeers: Int,
    val totalValid: Int,
    val agreeingPeers: Int,
    val accepted: Boolean,
    val dissenters: List<String>,
    val reason: String,
)

object GopaKeys {
    private val provider: Provider by lazy { BouncyCastleProvider() }
    fun signature(): Signature = Signature.getInstance("Ed25519", provider)
    fun generateEd25519(): KeyPair = KeyPairGenerator.getInstance("Ed25519", provider).generateKeyPair()
    fun publicKeyFromX509(base64: String): PublicKey = KeyFactory.getInstance("Ed25519", provider)
        .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(base64)))
    fun privateKeyFromPkcs8(base64: String): PrivateKey = KeyFactory.getInstance("Ed25519", provider)
        .generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)))
    fun encodePublic(key: PublicKey): String = Base64.getEncoder().encodeToString(key.encoded)
    fun encodePrivate(key: PrivateKey): String = Base64.getEncoder().encodeToString(key.encoded)
}

class GopaConsensus(
    peers: Collection<PeerIdentity>,
    private val maxFutureSkewMs: Long = 5L * 60 * 1000,
) {
    private val peerDirectory = peers.filter { it.enabled }.associateBy { it.peerId }
    private val receipts = linkedMapOf<String, MutableMap<String, Attestation>>() // artifact -> peer -> receipt

    init {
        require(peerDirectory.isNotEmpty()) { "É necessário registar pelo menos um peer elegível." }
        require(peerDirectory.size == peers.filter { it.enabled }.size) { "IDs de peer duplicados." }
        require(peerDirectory.keys.all { it.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) })
        peerDirectory.values.forEach { GopaKeys.publicKeyFromX509(it.publicKeyX509Base64) }
        require(maxFutureSkewMs in 1_000..60L * 60 * 1000)
    }

    fun createAttestation(
        artifactHash: String,
        verdict: AuditVerdict,
        evidenceHashes: List<String>,
        peerId: String,
        privateKey: PrivateKey,
        issuedAtEpochMs: Long = System.currentTimeMillis(),
    ): Attestation {
        requireHash(artifactHash)
        require(evidenceHashes.size in 1..10_000 && evidenceHashes.all(::isHash))
        require(peerId in peerDirectory) { "Peer não está registado ou não está habilitado." }
        val root = merkleRoot(evidenceHashes)
        val unsigned = Attestation(artifactHash.lowercase(), verdict, evidenceHashes.map { it.lowercase() }, root,
            issuedAtEpochMs, peerId, "")
        val signature = GopaKeys.signature().run {
            initSign(privateKey)
            update(canonicalPayload(unsigned))
            Base64.getEncoder().encodeToString(sign())
        }
        val signed = unsigned.copy(signatureBase64 = signature)
        require(verify(signed)) { "A chave privada não corresponde à identidade pública registada ou o provider Ed25519 não está disponível." }
        ingest(listOf(signed))
        return signed
    }

    /** Invalid, stale, duplicate, unknown-peer or malformed receipts are discarded. */
    @Synchronized
    fun ingest(remote: List<Attestation>, nowEpochMs: Long = System.currentTimeMillis()): Int {
        var accepted = 0
        remote.forEach { attestation ->
            if (!verify(attestation, nowEpochMs)) return@forEach
            val byPeer = receipts.getOrPut(attestation.artifactHash.lowercase()) { linkedMapOf() }
            if (attestation.peerId !in byPeer) {
                byPeer[attestation.peerId] = attestation
                accepted++
            }
        }
        return accepted
    }

    fun verify(attestation: Attestation, nowEpochMs: Long = System.currentTimeMillis()): Boolean = runCatching {
        requireHash(attestation.artifactHash)
        require(attestation.peerId in peerDirectory)
        require(attestation.evidenceHashes.isNotEmpty() && attestation.evidenceHashes.size <= 10_000)
        require(attestation.evidenceHashes.all(::isHash))
        require(attestation.evidenceRoot == merkleRoot(attestation.evidenceHashes))
        require(attestation.issuedAtEpochMs >= 0L)
        require(attestation.issuedAtEpochMs <= Math.addExact(nowEpochMs, maxFutureSkewMs))
        val publicKey = GopaKeys.publicKeyFromX509(peerDirectory.getValue(attestation.peerId).publicKeyX509Base64)
        GopaKeys.signature().run {
            initVerify(publicKey)
            update(canonicalPayload(attestation.copy(signatureBase64 = "")))
            verify(Base64.getDecoder().decode(attestation.signatureBase64))
        }
    }.getOrDefault(false)

    @Synchronized
    fun consensus(artifactHash: String): ConsensusResult {
        requireHash(artifactHash)
        val eligible = peerDirectory.size
        val required = (2 * eligible) / 3 + 1
        val valid = receipts[artifactHash.lowercase()].orEmpty().values.filter { verify(it) }
        val groups = valid.groupBy { it.verdict.name + ":" + it.evidenceRoot }
        val winner = groups.maxByOrNull { it.value.size }
        val winningCount = winner?.value?.size ?: 0
        val dissenters = if (winner == null) emptyList() else valid
            .filter { it.verdict.name + ":" + it.evidenceRoot != winner.key }.map { it.peerId }.sorted()
        val accepted = winner != null && winningCount >= required
        return ConsensusResult(artifactHash.lowercase(),
            if (accepted) winner!!.value.first().verdict.name else "NO_CONSENSUS",
            required, eligible, valid.size, winningCount, accepted, dissenters,
            when {
                valid.isEmpty() -> "Nenhuma atestação válida recebida."
                accepted -> "Quórum de auditoria atingido; não equivale a consenso BFT nem prova de independência operacional dos peers."
                else -> "Quórum insuficiente: são necessários $required peers distintos de $eligible elegíveis."
            })
    }

    @Synchronized
    fun gossipDelta(knownPeerArtifactIds: Set<String>): List<Attestation> = receipts.values.flatMap { it.values }
        .filter { "${it.peerId}:${it.artifactHash}" !in knownPeerArtifactIds }

    @Synchronized
    fun localSetHash(): String = sha256(receipts.values.flatMap { it.values }
        .map { "${it.peerId}:${it.artifactHash}:${it.evidenceRoot}" }.sorted().joinToString("\n").toByteArray())

    @Synchronized
    fun allAttestations(): List<Attestation> = receipts.values.flatMap { it.values }

    fun eligiblePeerCount(): Int = peerDirectory.size

    private fun canonicalPayload(a: Attestation): ByteArray = listOf(
        "GOPA-ATTESTATION-V1", a.artifactHash.lowercase(), a.verdict.name,
        a.evidenceRoot.lowercase(), a.issuedAtEpochMs.toString(), a.peerId,
        a.evidenceHashes.joinToString(",") { it.lowercase() },
    ).joinToString("\n").toByteArray(StandardCharsets.UTF_8)

    companion object {
        fun merkleRoot(hashes: List<String>): String {
            require(hashes.isNotEmpty() && hashes.all(::isHash))
            var level = hashes.map { hexToBytes(it.lowercase()) }
            while (level.size > 1) {
                val next = ArrayList<ByteArray>((level.size + 1) / 2)
                var i = 0
                while (i < level.size) {
                    val left = level[i]
                    val right = level.getOrElse(i + 1) { left }
                    next += MessageDigest.getInstance("SHA-256").digest(byteArrayOf(1) + left + right)
                    i += 2
                }
                level = next
            }
            return level.single().joinToString("") { "%02x".format(it) }
        }
        fun hashEvidence(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        private fun isHash(value: String) = value.matches(Regex("(?i)[a-f0-9]{64}"))
        private fun requireHash(value: String) = require(isHash(value)) { "SHA-256 inválido." }
        private fun hexToBytes(value: String) = ByteArray(value.length / 2) { i ->
            value.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        private fun sha256(bytes: ByteArray) = hashEvidence(bytes)
    }
}
