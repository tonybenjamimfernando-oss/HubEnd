package com.hubops.vislumbrops.analysis

import com.hubops.vislumbrops.core.AnalysisResult
import com.hubops.vislumbrops.core.CheckCategory
import com.hubops.vislumbrops.core.CheckResult
import com.hubops.vislumbrops.core.Finding
import com.hubops.vislumbrops.core.Severity
import com.hubops.vislumbrops.core.VerificationCheck
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipFile

/**
 * Independent APK supply-chain engine.
 *
 * It inventories archive entries without extracting them, hashes bounded content,
 * detects archive/path anomalies and produces a deterministic, exportable manifest.
 * It does not claim to validate APK signing blocks or identify every dependency.
 */
class ArtifactSupplyChainEngine(
    private val limits: Limits = Limits()
) {
    init {
        require(limits.maxArchiveBytes > 0L)
        require(limits.maxEntries > 0)
        require(limits.maxEntryBytes > 0L)
        require(limits.maxExpandedBytes > 0L)
        require(limits.suspiciousCompressionRatio > 1.0)
    }

    data class Limits(
        val maxArchiveBytes: Long = 100L * 1024 * 1024,
        val maxEntries: Int = 10_000,
        val maxEntryBytes: Long = 64L * 1024 * 1024,
        val maxExpandedBytes: Long = 300L * 1024 * 1024,
        val suspiciousCompressionRatio: Double = 200.0
    )

    data class InventoryEntry(
        val path: String,
        val size: Long,
        val compressedSize: Long,
        val sha256: String?,
        val kind: String
    )

    data class Manifest(
        val schemaVersion: String,
        val artifactName: String,
        val artifactSize: Long,
        val artifactSha256: String,
        val entryCount: Int,
        val expandedBytes: Long,
        val nativeLibraries: List<String>,
        val dexFiles: List<String>,
        val v1SignatureFiles: List<String>,
        val entries: List<InventoryEntry>,
        val manifestSha256: String
    ) {
        fun toJson(): String {
            fun q(value: String): String = buildString {
                append('"')
                value.forEach { char ->
                    when (char) {
                        '\\' -> append("\\\\")
                        '"' -> append("\\\"")
                        '\n' -> append("\\n")
                        '\r' -> append("\\r")
                        '\t' -> append("\\t")
                        '\b' -> append("\\b")
                        '\u000c' -> append("\\f")
                        else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char)
                    }
                }
                append('"')
            }
            val body = buildString {
                append("{")
                append("\"schemaVersion\":${q(schemaVersion)},")
                append("\"artifactName\":${q(artifactName)},")
                append("\"artifactSize\":$artifactSize,")
                append("\"artifactSha256\":${q(artifactSha256)},")
                append("\"manifestSha256\":${q(manifestSha256)},")
                append("\"entryCount\":$entryCount,")
                append("\"expandedBytes\":$expandedBytes,")
                append("\"nativeLibraries\":[${nativeLibraries.joinToString(",") { q(it) }}],")
                append("\"dexFiles\":[${dexFiles.joinToString(",") { q(it) }}],")
                append("\"v1SignatureFiles\":[${v1SignatureFiles.joinToString(",") { q(it) }}],")
                append("\"entries\":[")
                append(entries.joinToString(",") {
                    "{\"path\":${q(it.path)},\"size\":${it.size}," +
                        "\"compressedSize\":${it.compressedSize}," +
                        "\"sha256\":${it.sha256?.let(::q) ?: "null"},\"kind\":${q(it.kind)}}"
                })
                append("]}")
            }
            return body
        }
    }

    data class Audit(
        val result: AnalysisResult,
        val manifest: Manifest?
    )

    fun audit(artifactPath: String): Audit {
        val started = System.currentTimeMillis()
        val file = File(artifactPath)
        if (!file.isFile || !file.canRead()) {
            return failed("SCM-INPUT", "Artefacto inexistente ou sem leitura")
        }
        if (file.length() <= 0L || file.length() > limits.maxArchiveBytes) {
            return failed("SCM-SIZE", "Tamanho do APK fora dos limites definidos")
        }

        val initialArtifactHash = try {
            hashFile(file)
        } catch (_: Exception) {
            return failed("SCM-HASH-READ", "Não foi possível calcular o hash inicial do APK")
        }
        val findings = mutableListOf<Finding>()
        val entries = mutableListOf<InventoryEntry>()
        val seen = mutableSetOf<String>()
        val native = mutableListOf<String>()
        val dex = mutableListOf<String>()
        val v1 = mutableListOf<String>()
        var expanded = 0L
        var invalidPaths = 0
        var oversized = 0
        var suspiciousRatios = 0
        var duplicatePaths = 0
        var entriesSeen = 0

        try {
            ZipFile(file).use { zip ->
                val enumeration = zip.entries()
                while (enumeration.hasMoreElements()) {
                    val entry = enumeration.nextElement()
                    if (entriesSeen++ >= limits.maxEntries) {
                        return failed("SCM-ENTRY-LIMIT", "Limite de entradas ZIP excedido")
                    }

                    val rawPath = entry.name
                    val normalized = rawPath.replace('\\', '/')
                    val parts = normalized.split('/')
                    val unsafe = normalized.isBlank() || normalized.startsWith("/") ||
                        normalized.startsWith("//") ||
                        parts.any { it == ".." || it == "." } ||
                        Regex("^[A-Za-z]:").containsMatchIn(normalized) ||
                        normalized.contains('\u0000')
                    if (unsafe) {
                        invalidPaths++
                        findings += finding(
                            Severity.CRITICAL, "Caminho perigoso no APK",
                            "Entrada ZIP com caminho absoluto ou traversal: ${rawPath.take(180)}",
                            rawPath, "CWE-22"
                        )
                        entries += InventoryEntry(rawPath, entry.size, entry.compressedSize, null, "UNSAFE_PATH")
                        continue
                    }
                    if (entry.isDirectory) continue

                    val key = normalized.lowercase(Locale.ROOT)
                    if (!seen.add(key)) {
                        duplicatePaths++
                        findings += finding(
                            Severity.HIGH, "Entrada ZIP duplicada",
                            "Caminho repetido (ignorando maiúsculas/minúsculas): ${normalized.take(180)}",
                            normalized, "CWE-694"
                        )
                        entries += InventoryEntry(normalized, entry.size, entry.compressedSize, null, "DUPLICATE_PATH")
                        continue
                    }

                    val declaredSize = entry.size
                    val compressed = entry.compressedSize
                    if (declaredSize > limits.maxEntryBytes ||
                        (declaredSize >= 0 && expanded + declaredSize > limits.maxExpandedBytes)) {
                        oversized++
                        findings += finding(
                            Severity.HIGH, "Entrada ZIP excessivamente grande",
                            "Entrada excede os limites de inspeção; conteúdo não foi extraído.",
                            normalized, "CWE-409"
                        )
                        entries += InventoryEntry(normalized, declaredSize, compressed, null, kindOf(normalized))
                        continue
                    }

                    if (declaredSize > 1_048_576L && compressed > 0L &&
                        declaredSize.toDouble() / compressed.toDouble() > limits.suspiciousCompressionRatio) {
                        suspiciousRatios++
                        findings += finding(
                            Severity.HIGH, "Rácio de compressão suspeito",
                            "A entrada apresenta rácio de compressão anormalmente elevado.",
                            normalized, "CWE-409"
                        )
                    }

                    val digest = MessageDigest.getInstance("SHA-256")
                    var actual = 0L
                    var tooLarge = false
                    zip.getInputStream(entry).use { input ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            actual += count
                            expanded += count
                            if (actual > limits.maxEntryBytes || expanded > limits.maxExpandedBytes) {
                                tooLarge = true
                                break
                            }
                            digest.update(buffer, 0, count)
                        }
                    }
                    if (tooLarge) {
                        oversized++
                        findings += finding(
                            Severity.HIGH, "Limite de expansão atingido",
                            "A leitura foi interrompida para evitar consumo excessivo de memória/armazenamento.",
                            normalized, "CWE-409"
                        )
                    }
                    val hash = if (tooLarge) null else digest.digest().toHex()
                    entries += InventoryEntry(normalized, actual, compressed, hash, kindOf(normalized))

                    if (normalized.startsWith("lib/") && normalized.endsWith(".so", true)) native += normalized
                    if (Regex("classes(\\d*)\\.dex", RegexOption.IGNORE_CASE).matches(normalized)) dex += normalized
                    if (normalized.startsWith("META-INF/", true) &&
                        (normalized.endsWith(".RSA", true) || normalized.endsWith(".DSA", true) ||
                            normalized.endsWith(".EC", true))) v1 += normalized
                }
            }
        } catch (e: Exception) {
            return failed("SCM-ZIP-READ", "Falha ao ler estrutura ZIP: ${e.javaClass.simpleName}")
        }

        if (native.isEmpty()) {
            findings += finding(
                Severity.INFO, "Sem bibliotecas nativas identificadas",
                "Não foram encontradas entradas lib/<ABI>/*.so; isto não é, por si só, um problema."
            )
        }
        if (v1.isNotEmpty()) {
            findings += finding(
                Severity.INFO, "Metadados de assinatura v1 presentes",
                "Foram encontrados ficheiros de assinatura META-INF. A assinatura criptográfica não foi validada."
            )
        }

        val artifactHash = try {
            hashFile(file)
        } catch (_: Exception) {
            return failed("SCM-HASH-READ", "Não foi possível recalcular o hash final do APK")
        }
        if (artifactHash != initialArtifactHash) {
            return failed("SCM-ARTIFACT-CHANGED", "O APK foi alterado durante a auditoria; manifesto descartado")
        }
        val canonical = buildString {
            fun part(value: String) { append(value.length).append(':').append(value) }
            part("supply-chain-v1")
            part(artifactHash)
            part(file.length().toString())
            part(expanded.toString())
            entries.sortedWith(compareBy<InventoryEntry>(
                { it.path }, { it.kind }, { it.size }, { it.compressedSize }, { it.sha256.orEmpty() }
            )).forEach { entry ->
                part(entry.path)
                part(entry.size.toString())
                part(entry.compressedSize.toString())
                part(entry.sha256 ?: "UNHASHED")
                part(entry.kind)
            }
        }
        val manifestHash = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8)).toHex()
        val manifest = Manifest(
            schemaVersion = "1.0",
            artifactName = file.name,
            artifactSize = file.length(),
            artifactSha256 = artifactHash,
            entryCount = entries.size,
            expandedBytes = expanded,
            nativeLibraries = native.sorted(),
            dexFiles = dex.sorted(),
            v1SignatureFiles = v1.sorted(),
            entries = entries.sortedWith(compareBy<InventoryEntry>(
                { it.path }, { it.kind }, { it.size }, { it.compressedSize }, { it.sha256.orEmpty() }
            )),
            manifestSha256 = manifestHash
        )

        val result = when {
            invalidPaths > 0 -> CheckResult.FAIL
            oversized > 0 || duplicatePaths > 0 || suspiciousRatios > 0 -> CheckResult.WARN
            else -> CheckResult.PASS
        }
        val check = VerificationCheck(
            id = "SUPPLY-CHAIN-INVENTORY",
            category = CheckCategory.SUPPLY_CHAIN,
            description = "${entries.size} entradas; ${native.size} bibliotecas nativas; ${dex.size} ficheiros DEX; manifesto SHA-256 ${manifestHash.take(16)}",
            result = result,
            durationMs = System.currentTimeMillis() - started
        )
        return Audit(AnalysisResult(listOf(check), findings), manifest)
    }

    private fun failed(id: String, message: String) = Audit(
        AnalysisResult(
            checks = listOf(VerificationCheck(id, CheckCategory.SUPPLY_CHAIN, message, CheckResult.ERROR)),
            findings = listOf(finding(Severity.HIGH, "Auditoria de cadeia de fornecimento incompleta", message))
        ),
        null
    )

    private fun finding(
        severity: Severity,
        title: String,
        description: String,
        location: String? = null,
        cwe: String? = null
    ) = Finding(
        category = CheckCategory.SUPPLY_CHAIN,
        severity = severity,
        title = title,
        description = description,
        location = location,
        cweId = cwe,
        remediationHint = "Rever o pacote de origem e reconstruir o artefacto a partir de uma fonte confiável."
    )

    private fun hashFile(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(32 * 1024)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= limits.maxArchiveBytes) { "APK excedeu o limite durante leitura" }
            digest.update(buffer, 0, count)
        }
        digest.digest().toHex()
    }

    private fun kindOf(path: String): String = when {
        path.matches(Regex("classes(\\d*)\\.dex", RegexOption.IGNORE_CASE)) -> "DEX"
        path.startsWith("lib/") && path.endsWith(".so", true) -> "NATIVE_LIBRARY"
        path.startsWith("META-INF/") -> "SIGNATURE_OR_METADATA"
        path.endsWith(".xml", true) -> "XML_RESOURCE"
        path.startsWith("assets/") -> "ASSET"
        else -> "OTHER"
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
