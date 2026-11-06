package dev.hubops.omega.containers

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** Explicit, idempotent bootstrap. It does not run arbitrary commands at app startup. */
class AlpineBootstrap(private val context: Context, private val alpineSha256: String, private val prootSha256: String) {
    companion object { const val REF = "alpine:3.24.4"; const val ROOTFS_ASSET = "oci/alpine_3.24.4.tar.gz" }
    data class Report(val prootReady: Boolean, val rootfsReady: Boolean, val message: String)
    private val installer = RootfsInstaller(context.applicationContext)

    suspend fun ensure(): Report = withContext(Dispatchers.IO) {
        if (Build.SUPPORTED_ABIS.none { it == "arm64-v8a" })
            return@withContext Report(false, false, "Esta distribuição inclui PRoot apenas para arm64-v8a.")
        val proot = File(context.filesDir, "native/proot")
        val prootReady = runCatching {
            val nativeLibs = File(context.applicationInfo.nativeLibraryDir)
            val talloc = File(nativeLibs, "libtalloc.so")
            require(talloc.isFile) { "libtalloc.so não foi empacotada no APK; PRoot não pode ser iniciado." }
            val privateNative = proot.parentFile.apply { mkdirs() }
            val tallocAlias = File(privateNative, "libtalloc.so.2")
            if (!tallocAlias.isFile || !hash(tallocAlias).equals(hash(talloc), true)) {
                val aliasStage = File(privateNative, "libtalloc.so.2.part")
                talloc.inputStream().use { input -> aliasStage.outputStream().use { output -> input.copyTo(output) } }
                if (tallocAlias.exists()) tallocAlias.delete()
                require(aliasStage.renameTo(tallocAlias)) { "Não foi possível preparar libtalloc para o loader." }
            }
            require(prootSha256.matches(Regex("(?i)[a-f0-9]{64}")))
            if (!proot.isFile || !hash(proot).equals(prootSha256, true)) {
                val dir = proot.parentFile.apply { mkdirs() }
                val staged = File(dir, "proot-${System.nanoTime()}.part")
                val backup = File(dir, "proot.previous")
                if (!proot.exists() && backup.exists()) require(backup.renameTo(proot))
                if (proot.exists() && backup.exists()) backup.delete()
                try {
                    context.assets.open("native/arm64-v8a/proot").use { input -> staged.outputStream().use { output -> input.copyTo(output) } }
                    require(hash(staged).equals(prootSha256, true)) { "Hash do PRoot inválido." }
                    val magic = staged.inputStream().use { input -> ByteArray(4).also { require(input.read(it) == 4) } }
                    require(magic.contentEquals(byteArrayOf(0x7f, 0x45, 0x4c, 0x46))) { "Binário PRoot não é ELF." }
                    staged.setExecutable(true, true)
                    if (proot.exists()) require(proot.renameTo(backup)) { "Não foi possível preservar PRoot anterior." }
                    if (!staged.renameTo(proot)) {
                        if (backup.exists()) backup.renameTo(proot)
                        error("Não foi possível ativar PRoot; versão anterior restaurada.")
                    }
                    backup.delete()
                } finally { staged.delete() }
            }
            proot.setExecutable(true, true) && proot.canExecute()
        }.getOrDefault(false)
        if (!prootReady) return@withContext Report(false, false, "PRoot ausente, inválido ou incompatível; manter análise JVM.")
        val result = installer.install(REF, ROOTFS_ASSET, alpineSha256)
        if (result.isFailure) return@withContext Report(true, false, "Falha ao instalar Alpine: ${result.exceptionOrNull()?.message}")
        Report(true, true, "PRoot e Alpine instalados e verificados. PRoot continua a ser isolamento de espaço de utilizador, não isolamento de kernel.")
    }
    fun alpineRootfs(): File = File(context.filesDir, "oci/rootfs/alpine_3.24.4")
    private fun hash(file: File): String {
        val d = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val b=ByteArray(65536); while(true){val n=input.read(b);if(n<0)break;d.update(b,0,n)} }
        return d.digest().joinToString("") { "%02x".format(it) }
    }
}
