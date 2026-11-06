package dev.hubops.omega.ai

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.util.Base64
import dev.hubops.omega.merkle.GopaKeys

/** Installs a separately distributed GGUF only after manifest, size, hash and Ed25519 checks. */
class SignedModelInstaller(private val context: Context, private val releaseKey: PublicKey) {
    companion object { private val installLock = Any() }
    data class InstalledShard(val name: String, val file: File, val sha256: String, val sizeBytes: Long)

    /** Development path when a model was deliberately packaged as an APK asset. */
    fun install(shard: String): Result<InstalledShard> = runCatching { synchronized(installLock) {
        val manifest = readVerifiedManifest()
        val item = shardEntry(manifest, shard)
        context.assets.open("models/shards/${item.file}").use { input -> installStream(shard, item, input) }
    } }

    /** Production path: GGUF is distributed out-of-band to avoid a multi-gigabyte APK. */
    fun installFromFile(shard: String, source: File): Result<InstalledShard> = runCatching { synchronized(installLock) {
        require(source.isFile && !java.nio.file.Files.isSymbolicLink(source.toPath()))
        val manifest = readVerifiedManifest()
        val item = shardEntry(manifest, shard)
        require(source.length() == item.sizeBytes) { "Tamanho do GGUF não corresponde ao manifesto." }
        source.inputStream().use { input -> installStream(shard, item, input) }
    } }

    private data class Item(val file: String, val sizeBytes: Long, val sha256: String, val signature: ByteArray)
    private fun readVerifiedManifest(): JSONObject {
        val bytes = context.assets.open("models/shards/manifest.json").use { it.readBytes() }
        val signature = context.assets.open("models/shards/manifest.json.sig").use { it.readBytes() }
        verifySignature(bytes, signature)
        return JSONObject(String(bytes, Charsets.UTF_8)).also {
            require(it.getInt("schema") == 1) { "Versão do manifesto de modelos não suportada." }
        }
    }
    private fun shardEntry(manifest: JSONObject, shard: String): Item {
        require(shard in setOf("sqli", "crypto", "auth", "network", "storage", "logging", "deserialization"))
        val row = manifest.getJSONObject("shards").getJSONObject(shard)
        val file = row.getString("file")
        require(file == "$shard.gguf")
        val size = row.getLong("sizeBytes")
        val hash = row.getString("sha256")
        require(size in 1..(2L * 1024 * 1024 * 1024))
        require(hash.matches(Regex("(?i)[a-f0-9]{64}")))
        val sig = Base64.getDecoder().decode(row.getString("signatureBase64"))
        require(sig.size == 64) { "Assinatura Ed25519 do shard tem tamanho inválido." }
        return Item(file, size, hash, sig)
    }
    private fun installStream(shard: String, item: Item, input: InputStream): InstalledShard {
        val root = File(context.filesDir, "models/shards").apply { mkdirs() }.canonicalFile
        val destination = File(root, item.file).canonicalFile
        require(destination.parentFile == root)
        val staged = File(root, "${item.file}.part")
        val backup = File(root, "${item.file}.previous")
        if (!destination.exists() && backup.exists()) require(backup.renameTo(destination)) { "Não foi possível recuperar o shard anterior." }
        if (destination.exists() && backup.exists()) backup.delete()
        val digest = MessageDigest.getInstance("SHA-256")
        var copied = 0L
        return try {
            staged.outputStream().use { output ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val n = input.read(buffer); if (n < 0) break
                    copied = Math.addExact(copied, n.toLong())
                    require(copied <= item.sizeBytes) { "Modelo excede tamanho declarado no manifesto." }
                    digest.update(buffer, 0, n); output.write(buffer, 0, n)
                }
            }
            require(copied == item.sizeBytes) { "Tamanho do modelo não corresponde ao manifesto." }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            require(actual.equals(item.sha256, true)) { "SHA-256 do modelo inválido." }
            verifyFileSignature(staged, item.signature)
            if (destination.exists()) require(destination.renameTo(backup)) { "Não foi possível preservar o shard anterior." }
            if (!staged.renameTo(destination)) {
                if (backup.exists()) backup.renameTo(destination)
                error("Não foi possível ativar o modelo; shard anterior restaurado.")
            }
            backup.delete()
            InstalledShard(shard, destination, actual, copied)
        } finally { staged.delete() }
    }
    private fun verifySignature(bytes: ByteArray, signatureBytes: ByteArray) {
        require(bytes.size <= 1_000_000 && signatureBytes.size == 64)
        val verifier = GopaKeys.signature()
        verifier.initVerify(releaseKey); verifier.update(bytes)
        require(verifier.verify(signatureBytes)) { "Assinatura Ed25519 do manifesto inválida." }
    }
    private fun verifyFileSignature(file: File, signatureBytes: ByteArray) {
        val verifier = GopaKeys.signature()
        verifier.initVerify(releaseKey)
        file.inputStream().use { input -> val buffer = ByteArray(1024 * 1024); while (true) { val n=input.read(buffer); if(n<0)break; verifier.update(buffer,0,n) } }
        require(verifier.verify(signatureBytes)) { "Assinatura Ed25519 do GGUF inválida." }
    }
}
