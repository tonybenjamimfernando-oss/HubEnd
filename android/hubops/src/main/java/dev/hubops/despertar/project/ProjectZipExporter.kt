package dev.hubops.despertar.project

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import java.security.MessageDigest

/** Cópia controlada do ZIP gerado para uma localização que o Termux consegue ler. */
data class ExportedProjectZip(
    val displayName: String,
    val sharedPath: String,
    val sizeBytes: Long,
    val sha256: String,
)

object ProjectZipExporter {
    private const val MAX_PROJECT_ZIP_BYTES = 50L * 1024L * 1024L

    fun export(context: Context, source: File, projectKey: String): ExportedProjectZip {
        // HubEnd: guarda explícita de versão reconhecida pelo lint (o require() original não era).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw IllegalArgumentException("A exportação para Downloads/HubOps requer Android 10 ou superior.")
        }
        return exportToDownloads(context, source, projectKey)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun exportToDownloads(context: Context, source: File, projectKey: String): ExportedProjectZip {
        require(projectKey.matches(Regex("^[A-Za-z0-9._-]{3,60}$"))) { "Identificador de projeto inválido." }
        require(source.isFile && source.length() in 1..MAX_PROJECT_ZIP_BYTES) {
            "O ZIP do projeto não existe, está vazio ou excede 50 MB."
        }
        val resolver = context.contentResolver
        val displayName = "$projectKey.zip"
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/HubOps/Projects")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = resolver.insert(collection, values)
            ?: error("Não foi possível criar o ZIP em Downloads/HubOps/Projects.")
        val digest = MessageDigest.getInstance("SHA-256")
        var copied = 0L
        try {
            source.inputStream().buffered().use { input ->
                val header = ByteArray(4)
                var headerBytes = 0
                while (headerBytes < header.size) {
                    val count = input.read(header, headerBytes, header.size - headerBytes)
                    if (count < 0) break
                    headerBytes += count
                }
                require(headerBytes == 4 && header[0] == 0x50.toByte() && header[1] == 0x4b.toByte() &&
                    header[2] in setOf(0x03.toByte(), 0x05.toByte(), 0x07.toByte())) {
                    "O ficheiro gerado não tem um cabeçalho ZIP válido."
                }
                resolver.openOutputStream(target, "w")?.buffered()?.use { output ->
                    output.write(header)
                    digest.update(header)
                    copied += header.size
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count
                        require(copied <= MAX_PROJECT_ZIP_BYTES) { "O ZIP excedeu o limite de exportação." }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                } ?: error("Não foi possível escrever o ZIP no armazenamento partilhado.")
            }
            val ready = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            check(resolver.update(target, ready, null, null) == 1) { "Não foi possível concluir a exportação do ZIP." }
            return ExportedProjectZip(
                displayName = displayName,
                sharedPath = "/storage/emulated/0/Download/HubOps/Projects/$displayName",
                sizeBytes = copied,
                sha256 = digest.digest().joinToString("") { "%02x".format(it) },
            )
        } catch (error: Exception) {
            resolver.delete(target, null, null)
            throw error
        }
    }
}
