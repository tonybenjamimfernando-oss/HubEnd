package dev.hubops.despertar

import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Inspeção estrutural local de APK/ZIP. Não extrai ficheiros nem executa conteúdo.
 * Limites explícitos reduzem risco de ZIP bombs e consumo excessivo de memória/CPU.
 */
data class ApkArchiveReport(
    val entries: Int,
    val dexFiles: List<String>,
    val nativeLibraries: Int,
    val resourceFiles: Int,
    val assetFiles: Int,
    val hasManifest: Boolean,
    val hasResourcesTable: Boolean,
    val signatureFiles: List<String>,
    val uncompressedBytesRead: Long,
    val truncated: Boolean,
    val warnings: List<String>,
) {
    fun asText(): String = buildString {
        appendLine("Entradas ZIP analisadas: $entries")
        appendLine("DEX: ${dexFiles.size}${if (dexFiles.isNotEmpty()) " (${dexFiles.joinToString()})" else ""}")
        appendLine("Bibliotecas nativas: $nativeLibraries")
        appendLine("Ficheiros de recursos: $resourceFiles · assets: $assetFiles")
        appendLine("AndroidManifest.xml: ${if (hasManifest) "presente" else "não localizado"}")
        appendLine("resources.arsc: ${if (hasResourcesTable) "presente" else "não localizado"}")
        appendLine("Ficheiros de assinatura: ${signatureFiles.size}")
        appendLine("Dados descomprimidos lidos: $uncompressedBytesRead bytes")
        if (truncated) appendLine("ATENÇÃO: inspeção parcial; foi atingido um limite de segurança.")
        warnings.forEach { appendLine("Aviso: $it") }
        append("A inspeção é estrutural; não valida o certificado, não descodifica o manifesto binário e não prova que o APK é seguro.")
    }
}

object ApkArchiveInspector {
    private const val MAX_ENTRIES = 20_000
    private const val MAX_ENTRY_BYTES = 64L * 1024L * 1024L
    private const val MAX_TOTAL_BYTES = 256L * 1024L * 1024L
    private const val BUFFER_SIZE = 32 * 1024
    private val DEX_NAME = Regex("classes(\\d*)\\.dex")

    fun inspect(input: InputStream): ApkArchiveReport {
        var entries = 0
        var uncompressed = 0L
        var truncated = false
        var hasManifest = false
        var hasResources = false
        var nativeCount = 0
        var resourceCount = 0
        var assetCount = 0
        val dex = mutableListOf<String>()
        val signatures = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val buffer = ByteArray(BUFFER_SIZE)

        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = try { zip.nextEntry } catch (e: Exception) {
                    warnings += "Arquivo ZIP danificado ou entrada ilegível: ${e.javaClass.simpleName}."
                    truncated = true
                    break
                } ?: break
                entries++
                if (entries > MAX_ENTRIES) {
                    warnings += "Limite de $MAX_ENTRIES entradas atingido."
                    truncated = true
                    break
                }
                val name = entry.name.replace('\\', '/')
                if (name.startsWith("/") || name.split('/').any { it == ".." }) {
                    warnings += "O arquivo contém um caminho de entrada suspeito; a inspeção foi interrompida."
                    truncated = true
                    break
                }
                if (!entry.isDirectory) {
                    when {
                        name == "AndroidManifest.xml" -> hasManifest = true
                        name == "resources.arsc" -> hasResources = true
                        DEX_NAME.matches(name.substringAfterLast('/')) -> if (dex.size < 32) dex += name
                        name.startsWith("lib/") && name.endsWith(".so", true) -> nativeCount++
                        name.startsWith("res/") -> resourceCount++
                        name.startsWith("assets/") -> assetCount++
                        name.startsWith("META-INF/") && (name.endsWith(".RSA", true) || name.endsWith(".DSA", true) || name.endsWith(".EC", true) || name.endsWith(".SF", true)) -> if (signatures.size < 20) signatures += name
                    }
                    var entryBytes = 0L
                    while (true) {
                        val read = try { zip.read(buffer) } catch (e: Exception) {
                            warnings += "Não foi possível ler completamente uma entrada ZIP."
                            truncated = true
                            break
                        }
                        if (read < 0) break
                        entryBytes += read
                        uncompressed += read
                        if (entryBytes > MAX_ENTRY_BYTES || uncompressed > MAX_TOTAL_BYTES) {
                            warnings += "Limite de descompressão atingido (64 MB por entrada / 256 MB total)."
                            truncated = true
                            break
                        }
                    }
                    if (truncated) break
                }
                try { zip.closeEntry() } catch (_: Exception) { truncated = true; break }
            }
        }
        if (!hasManifest) warnings += "AndroidManifest.xml não foi localizado na raiz do APK."
        if (dex.isEmpty()) warnings += "Nenhum ficheiro DEX reconhecido."
        return ApkArchiveReport(entries, dex, nativeCount, resourceCount, assetCount, hasManifest,
            hasResources, signatures, uncompressed, truncated, warnings)
    }
}
