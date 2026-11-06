package dev.hubops.omega

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.net.Uri
import dev.hubops.BuildConfig
import dev.hubops.omega.ai.NoShardInferenceRuntime
import dev.hubops.omega.ai.ShardInferenceRuntime
import dev.hubops.omega.ai.SignedModelInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.hubops.omega.ai.AndroidLlamaCliRuntime
import java.security.PublicKey
import dev.hubops.omega.merkle.GopaKeys
import dev.hubops.omega.ai.SnseEngine
import dev.hubops.omega.containers.AlpineBootstrap
import dev.hubops.omega.containers.SignedAssetManifest
import dev.hubops.omega.containers.NestedSandboxCascade
import dev.hubops.omega.containers.SandboxCapabilities
import dev.hubops.omega.merkle.GopaConsensus
import dev.hubops.omega.merkle.PeerIdentity
import java.io.File

/** Lifecycle-owned entry point for the three Omega sub-engines. */
class OmegaEngineSuite(
    context: Context,
    inferenceRuntime: ShardInferenceRuntime = NoShardInferenceRuntime,
) {
    private val appContext = context.applicationContext
    val snse = SnseEngine(File(appContext.filesDir, "models/shards"), inferenceRuntime,
        ramBudgetMb = { availableRamMb() * 45 / 100 })
    val sandbox = NestedSandboxCascade(appContext.filesDir) { detectSandboxCapabilities() }

    /** Release public key is embedded by the signed release build; private key is never shipped. */
    fun releasePublicKeyOrNull(): PublicKey? = runCatching {
        val encoded = BuildConfig.OMEGA_RELEASE_PUBLIC_KEY_B64
        require(encoded.isNotBlank()) { "Chave pública Omega não configurada nesta build." }
        GopaKeys.publicKeyFromX509(encoded)
    }.getOrNull()

    /** Installs the signed llama.cpp executable and attaches the real CLI inference runtime. */
    suspend fun installLlamaCliRuntime(releaseKey: PublicKey): Result<Unit> = withContext(Dispatchers.IO) { runCatching {
        require(Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a") { "Runtime preparado apenas para arm64-v8a nesta entrega." }
        val expected = SignedAssetManifest(appContext, releaseKey).sha256("native/arm64-v8a/llama-cli")
        val nativeLibs = File(appContext.applicationInfo.nativeLibraryDir)
        require(File(nativeLibs, "libllama.so").isFile && nativeLibs.listFiles()?.any { it.name.startsWith("libggml") && it.extension == "so" } == true) {
            "Bibliotecas partilhadas llama/ggml não foram empacotadas no APK."
        }
        val dir = File(appContext.filesDir, "native").apply { mkdirs() }
        val binary = File(dir, "llama-cli")
        val staged = File(dir, "llama-cli-${System.nanoTime()}.part")
        val backup = File(dir, "llama-cli.previous")
        if (!binary.exists() && backup.exists()) require(backup.renameTo(binary)) { "Não foi possível recuperar o runtime anterior." }
        if (binary.exists() && backup.exists()) backup.delete()
        try {
            appContext.assets.open("native/arm64-v8a/llama-cli").use { input -> staged.outputStream().use { output -> input.copyTo(output) } }
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            staged.inputStream().use { input -> val b=ByteArray(65536); while(true){val n=input.read(b);if(n<0)break;digest.update(b,0,n)} }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            require(actual.equals(expected, true)) { "Hash do llama-cli inválido." }
            val magic = staged.inputStream().use { input -> ByteArray(4).also { require(input.read(it) == 4) } }
            require(magic.contentEquals(byteArrayOf(0x7f, 0x45, 0x4c, 0x46))) { "llama-cli não é ELF." }
            staged.setExecutable(true, true)
            if (binary.exists()) require(binary.renameTo(backup)) { "Não foi possível preservar runtime anterior." }
            if (!staged.renameTo(binary)) {
                if (backup.exists()) backup.renameTo(binary)
                error("Não foi possível ativar llama-cli; runtime anterior restaurado.")
            }
            backup.delete()
            snse.attachRuntime(AndroidLlamaCliRuntime(appContext, binary))
        } finally { staged.delete() }
    } }

    /** Install a deliberately APK-bundled model (development only). */
    fun installSnseShardAsset(releaseKey: PublicKey, shard: String) =
        SignedModelInstaller(appContext, releaseKey).install(shard)

    /** Import one separately distributed GGUF selected through Android's document picker. */
    suspend fun installSnseShard(releaseKey: PublicKey, shard: String, source: Uri) = withContext(Dispatchers.IO) {
        val staged = File(appContext.cacheDir, "import-${shard}-${System.nanoTime()}.gguf")
        try {
            val input = appContext.contentResolver.openInputStream(source)
                ?: error("Não foi possível abrir o ficheiro de modelo selecionado.")
            input.use { stream -> staged.outputStream().use { output ->
                val buffer = ByteArray(1024 * 1024); var total = 0L
                while (true) {
                    val n = stream.read(buffer); if (n < 0) break
                    total = Math.addExact(total, n.toLong())
                    require(total <= 2L * 1024 * 1024 * 1024) { "Modelo excede o limite de importação de 2 GB." }
                    output.write(buffer, 0, n)
                }
            } }
            SignedModelInstaller(appContext, releaseKey).installFromFile(shard, staged)
        } catch (e: Exception) { Result.failure(e) }
        finally { staged.delete() }
    }

    /** Explicit bootstrap; callers supply release hashes from a signed release manifest. */
    suspend fun bootstrapAlpine(releaseKey: PublicKey) = runCatching {
        val manifest = SignedAssetManifest(appContext, releaseKey)
        val alpineHash = manifest.sha256("oci/alpine_3.24.4.tar.gz")
        val prootHash = manifest.sha256("native/arm64-v8a/proot")
        AlpineBootstrap(appContext, alpineHash, prootHash).ensure()
    }.getOrElse { dev.hubops.omega.containers.AlpineBootstrap.Report(false, false,
        "Manifesto de release não validado: ${it.message}") }

    /** Peer registry is supplied by an administrator; no implicit trust or peers are created. */
    fun createGopa(peers: Collection<PeerIdentity>): GopaConsensus = GopaConsensus(peers)

    private fun availableRamMb(): Long {
        val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        return memory.availMem / (1024 * 1024)
    }

    private fun detectSandboxCapabilities(): SandboxCapabilities {
        val availableRamMb = availableRamMb()
        val nativeRoot = File(appContext.filesDir, "native")
        val proot = File(nativeRoot, "proot")
        val rootfs = File(appContext.filesDir, "oci/rootfs/alpine_3.24.4")
        val termux = isTermuxInstalled()
        return SandboxCapabilities(availableRamMb, proot, rootfs, termux, File(appContext.applicationInfo.nativeLibraryDir))
    }

    @Suppress("DEPRECATION")
    private fun isTermuxInstalled(): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 33) appContext.packageManager.getPackageInfo("com.termux",
            android.content.pm.PackageManager.PackageInfoFlags.of(0))
        else appContext.packageManager.getPackageInfo("com.termux", 0)
        true
    }.getOrDefault(false)
}
