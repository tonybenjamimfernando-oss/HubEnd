package dev.hubops.omega.containers

import android.content.Context
import org.json.JSONObject
import java.security.PublicKey
import dev.hubops.omega.merkle.GopaKeys

/** Verifies the release manifest before trusting the embedded artifact hashes. */
class SignedAssetManifest(private val context: Context, private val releaseKey: PublicKey) {
    fun sha256(assetPath: String): String {
        val bytes = context.assets.open("oci/release-manifest.json").use { it.readBytes() }
        val signatureBytes = context.assets.open("oci/release-manifest.json.sig").use { it.readBytes() }
        val verifier = GopaKeys.signature()
        verifier.initVerify(releaseKey); verifier.update(bytes)
        require(verifier.verify(signatureBytes)) { "Assinatura do manifesto NSC inválida." }
        val manifest = JSONObject(String(bytes, Charsets.UTF_8))
        require(manifest.getInt("schema") == 1) { "Versão do manifesto NSC não suportada." }
        val artifacts = manifest.getJSONObject("artifacts")
        val hash = artifacts.getJSONObject(assetPath).getString("sha256")
        require(hash.matches(Regex("(?i)[a-f0-9]{64}"))) { "Hash de artefacto inválido no manifesto." }
        return hash
    }
}
