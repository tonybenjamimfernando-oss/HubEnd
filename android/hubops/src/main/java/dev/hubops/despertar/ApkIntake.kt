package dev.hubops.despertar

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.security.MessageDigest

/** Importa um APK escolhido pelo utilizador para Downloads/HubOps, acessível ao Termux. */
data class StagedApk(
    val displayName: String,
    val sharedPath: String,
    val sizeBytes: Long,
    val sha256: String,
    val archive: ApkArchiveReport,
)

class ApkIntake(private val context: Context) {
    fun import(uri: Uri): StagedApk {
        // HubEnd: guarda explícita de versão reconhecida pelo lint (o require() original não era).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw IllegalArgumentException("A importação direta para a pasta partilhada requer Android 10 ou superior.")
        }
        return importToDownloads(uri)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun importToDownloads(uri: Uri): StagedApk {
        val resolver = context.contentResolver
        val name = "hubops-${System.currentTimeMillis()}.apk"
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/HubOps")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = resolver.insert(collection, values)
            ?: error("Não foi possível criar o ficheiro em Downloads/HubOps.")
        var total = 0L
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            val input = resolver.openInputStream(uri) ?: error("Não foi possível ler o APK selecionado.")
            val output = resolver.openOutputStream(target, "w") ?: error("Não foi possível gravar o APK.")
            input.use { raw -> output.use { sink ->
                val source = raw.buffered()
                val header = ByteArray(4)
                var headerSize = 0
                while (headerSize < header.size) {
                    val read = source.read(header, headerSize, header.size - headerSize)
                    if (read < 0) break
                    headerSize += read
                }
                require(headerSize == 4 && header[0] == 0x50.toByte() && header[1] == 0x4b.toByte() &&
                    header[2] == 0x03.toByte() && header[3] == 0x04.toByte()) {
                    "O ficheiro selecionado não começa com um cabeçalho ZIP/APK válido."
                }
                total = headerSize.toLong()
                digest.update(header)
                sink.write(header)
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_APK_BYTES) { "O APK excede o limite de 500 MB." }
                    digest.update(buffer, 0, count)
                    sink.write(buffer, 0, count)
                }
            } }
            val ready = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(target, ready, null, null)
            val archiveInput = resolver.openInputStream(target) ?: error("Não foi possível reabrir o APK para inspeção estrutural.")
            val archiveReport = ApkArchiveInspector.inspect(archiveInput)
            require(archiveReport.entries > 0 && archiveReport.hasManifest) {
                "O ficheiro é um ZIP, mas não foi reconhecido como APK Android válido (manifesto ausente)."
            }
            return StagedApk(
                displayName = name,
                sharedPath = "/storage/emulated/0/Download/HubOps/$name",
                sizeBytes = total,
                sha256 = digest.digest().joinToString("") { "%02x".format(it) },
                archive = archiveReport,
            )
        } catch (e: Exception) {
            resolver.delete(target, null, null)
            throw e
        }
    }

    companion object { private const val MAX_APK_BYTES = 500L * 1024L * 1024L }
}
