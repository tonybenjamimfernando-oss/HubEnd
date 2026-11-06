package dev.hubops.omega.containers

import android.content.Context
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.zip.GZIPInputStream

/** Transactional, bounded rootfs installer. Symlinks and special TAR entries are rejected. */
class RootfsInstaller(private val context: Context) {
    private val root = File(context.filesDir, "oci/rootfs")

    fun isInstalled(ref: String): Boolean = safeTarget(ref).let { it.isDirectory && File(it, ".ready").isFile }

    fun install(ref: String, assetPath: String, expectedSha256: String,
                maxExpandedBytes: Long = 768L * 1024 * 1024, maxEntries: Int = 30_000): Result<File> = runCatching {
        require(ref.matches(Regex("[A-Za-z0-9._-]+:[A-Za-z0-9._-]+")))
        require(expectedSha256.matches(Regex("(?i)[a-f0-9]{64}")))
        require(maxExpandedBytes in 1..(2L * 1024 * 1024 * 1024) && maxEntries in 1..100_000)
        root.mkdirs()
        val target = safeTarget(ref)
        if (isInstalled(ref) && File(target, ".ready").readText().contains("sha256=$expectedSha256")) return@runCatching target
        val stage = File(root, ".${target.name}.install-${System.nanoTime()}")
        val archive = File(context.cacheDir, "rootfs-${target.name}.tar.gz")
        try {
            context.assets.open(assetPath).use { input -> archive.outputStream().use { output -> input.copyTo(output) } }
            require(sha256(archive).equals(expectedSha256, true)) { "SHA-256 do rootfs não corresponde ao manifesto." }
            require(!stage.exists() && stage.mkdirs()) { "Não foi possível criar diretório temporário." }
            var entries = 0
            var expanded = 0L
            GZIPInputStream(FileInputStream(archive)).use { gzip ->
                TarArchiveInputStream(gzip).use { tar ->
                    while (true) {
                        val entry = tar.nextTarEntry ?: break
                        entries++
                        require(entries <= maxEntries) { "Limite de entradas TAR excedido." }
                        require(!entry.isLink) { "Hardlinks TAR não são permitidos: ${entry.name}" }
                        val relative = File(entry.name).toPath().normalize()
                        require(!relative.isAbsolute && relative.nameCount > 0 && relative.none { it.toString() == ".." }) {
                            "Path traversal TAR detetado: ${entry.name}"
                        }
                        val out = File(stage, relative.toString()).canonicalFile
                        require(out.path == stage.canonicalPath || out.path.startsWith(stage.canonicalPath + File.separator)) {
                            "Entrada fora do rootfs."
                        }
                        if (entry.isDirectory) { out.mkdirs(); continue }
                        if (entry.isSymbolicLink) {
                            val rawTarget = File(entry.linkName).toPath()
                            val targetPath = if (rawTarget.isAbsolute) stage.toPath().resolve(rawTarget.toString().removePrefix("/")).normalize()
                                else out.parentFile.toPath().resolve(rawTarget).normalize()
                            val canonicalTarget = targetPath.toFile().canonicalFile
                            require(canonicalTarget.path.startsWith(stage.canonicalPath + File.separator) || canonicalTarget.path == stage.canonicalPath) {
                                "Link simbólico tenta sair do rootfs: ${entry.name} -> ${entry.linkName}"
                            }
                            out.parentFile?.mkdirs()
                            val relativeTarget = out.parentFile.toPath().relativize(canonicalTarget.toPath())
                            Files.createSymbolicLink(out.toPath(), relativeTarget)
                            continue
                        }
                        require(entry.isFile && entry.size >= 0) { "Tipo de entrada TAR não suportado." }
                        require(!Files.exists(out.toPath(), LinkOption.NOFOLLOW_LINKS)) { "Entrada TAR duplicada: ${entry.name}" }
                        expanded = Math.addExact(expanded, entry.size)
                        require(expanded <= maxExpandedBytes) { "Limite de expansão TAR excedido." }
                        out.parentFile?.mkdirs()
                        out.outputStream().use { output ->
                            val buffer = ByteArray(32 * 1024)
                            var remaining = entry.size
                            while (remaining > 0) {
                                val n = tar.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                                if (n < 0) error("Entrada TAR truncada: ${entry.name}")
                                output.write(buffer, 0, n); remaining -= n
                            }
                        }
                        if ((entry.mode and 0b001001001) != 0) out.setExecutable(true, true)
                    }
                }
            }
            File(stage, ".ready").writeText("ref=$ref\nsha256=$expectedSha256\nexpandedBytes=$expanded\n")
            val backup = File(root, ".${target.name}.backup-${System.nanoTime()}")
            if (target.exists()) require(target.renameTo(backup)) { "Não foi possível preservar rootfs anterior." }
            if (!stage.renameTo(target)) {
                if (backup.exists()) backup.renameTo(target)
                error("Não foi possível ativar rootfs instalado; versão anterior restaurada.")
            }
            backup.deleteRecursively()
            target
        } finally { archive.delete(); if (stage.exists()) stage.deleteRecursively() }
    }

    private fun safeTarget(ref: String): File {
        val target = File(root, ref.replace(':', '_')).canonicalFile
        require(target.path.startsWith(root.canonicalFile.path + File.separator))
        return target
    }
    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val b = ByteArray(64 * 1024); while (true) { val n=input.read(b); if(n<0) break; digest.update(b,0,n) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
