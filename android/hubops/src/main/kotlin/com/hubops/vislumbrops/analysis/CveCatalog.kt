package com.hubops.vislumbrops.analysis

import com.hubops.vislumbrops.core.Severity
import org.json.JSONArray

/**
 * Comparação de versões numéricas pontuadas (ex.: 2.14.1, 2.9.10.3).
 * Uma letra final isolada vale como componente extra (estilo OpenSSL: 1.0.1g == [1,0,1,7]).
 * Outros qualificadores (-beta9, .RELEASE) são ignorados: só o prefixo numérico conta.
 * Funciona em JVM pura; não depende de classes Android.
 */
object VersionComparator {
    private val VERSION = Regex("^(\\d+(?:\\.\\d+)*)([a-z](?![a-z0-9]))?")

    fun parse(version: String): List<Long>? {
        val match = VERSION.find(version.trim()) ?: return null
        val parts = ArrayList<Long>()
        for (piece in match.groupValues[1].split('.')) {
            parts += piece.toLongOrNull() ?: return null
        }
        val letter = match.groupValues[2]
        if (letter.isNotEmpty()) parts += (letter[0] - 'a' + 1).toLong()
        return parts
    }

    /** Compara componente a componente; componentes em falta valem 0 (2.1 == 2.1.0). */
    fun compare(a: List<Long>, b: List<Long>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0L }
            val y = b.getOrElse(i) { 0L }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }
}

/** Intervalo semiaberto [introduced, fixed). Pelo menos um limite é obrigatório e ambos têm de ser numéricos. */
data class VersionRange(val introduced: String?, val fixed: String?) {
    init {
        require(introduced != null || fixed != null) { "intervalo sem limites" }
        introduced?.let { require(VersionComparator.parse(it) != null) { "limite 'introduced' inválido: $it" } }
        fixed?.let { require(VersionComparator.parse(it) != null) { "limite 'fixed' inválido: $it" } }
    }

    fun contains(version: String): Boolean {
        val v = VersionComparator.parse(version) ?: return false
        val low = introduced?.let { VersionComparator.parse(it) }
        val high = fixed?.let { VersionComparator.parse(it) }
        if (low != null && VersionComparator.compare(v, low) < 0) return false
        if (high != null && VersionComparator.compare(v, high) >= 0) return false
        return true
    }
}

data class CveEntry(
    val library: String,
    val aliases: List<String>,
    val ranges: List<VersionRange>,
    /** Prefixos legados ("4.1") com correspondência por fronteira: "4.1" casa 4.1 e 4.1.x, nunca 4.10. */
    val legacyPrefixes: List<String>,
    val cve: String,
    val severity: Severity,
    val description: String,
    val reference: String
) {
    /** Nomes minúsculos sob os quais a biblioteca pode aparecer em caminhos ou artifactIds. */
    val names: List<String> = (listOf(library) + aliases).map { it.lowercase() }.distinct()

    fun isAffected(version: String): Boolean {
        if (ranges.any { it.contains(version) }) return true
        val v = version.trim()
        return legacyPrefixes.any { v == it || v.startsWith("$it.") }
    }
}

object CveCatalog {
    data class Parsed(val entries: List<CveEntry>, val rejected: List<String>)

    /** Entradas inválidas são rejeitadas e reportadas; nunca interpretadas de forma permissiva. */
    fun parse(json: String): Parsed {
        val array = JSONArray(json)
        val entries = ArrayList<CveEntry>()
        val rejected = ArrayList<String>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i)
            if (o == null) {
                rejected += "#$i: não é um objeto"
                continue
            }
            try {
                val ranges = ArrayList<VersionRange>()
                o.optJSONArray("ranges")?.let { a ->
                    for (j in 0 until a.length()) {
                        val r = a.getJSONObject(j)
                        ranges += VersionRange(
                            if (r.isNull("introduced")) null else r.getString("introduced"),
                            if (r.isNull("fixed")) null else r.getString("fixed")
                        )
                    }
                }
                val legacy = o.optJSONArray("affected")?.let { a ->
                    (0 until a.length()).map { a.getString(it) }
                } ?: emptyList()
                require(ranges.isNotEmpty() || legacy.isNotEmpty()) { "sem intervalos de versão" }
                val cve = o.getString("cve")
                require(Regex("^CVE-\\d{4}-\\d{4,}$").matches(cve)) { "identificador CVE inválido: $cve" }
                entries += CveEntry(
                    library = o.getString("library"),
                    aliases = o.optJSONArray("aliases")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
                    ranges = ranges,
                    legacyPrefixes = legacy,
                    cve = cve,
                    severity = Severity.valueOf(o.getString("severity")),
                    description = o.getString("description"),
                    reference = if (o.has("reference")) o.getString("reference") else "https://nvd.nist.gov/vuln/detail/$cve"
                )
            } catch (e: Exception) {
                rejected += "#$i: ${e.message ?: e::class.java.simpleName}"
            }
        }
        return Parsed(entries, rejected)
    }

    /** Extrai a versão de nomes como "okhttp-4.9.2.jar" ou "libfoo_1.2.so" para o primeiro nome que coincidir. */
    fun extractVersion(path: String, names: List<String>): String? {
        val lower = path.lowercase()
        for (name in names) {
            val regex = Regex(Regex.escape(name) + "[_-]?(\\d+(?:\\.\\d+){1,3})")
            regex.find(lower)?.groupValues?.get(1)?.let { return it }
        }
        return null
    }

    /** Lê `version=` de um META-INF/maven/<grupo>/<artefacto>/pom.properties. */
    fun versionFromPomProperties(text: String): String? =
        text.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("version=") }
            ?.substringAfter('=')
            ?.trim()
            ?.takeIf { VersionComparator.parse(it) != null }
}
