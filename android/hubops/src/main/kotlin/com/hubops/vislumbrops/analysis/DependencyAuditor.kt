package com.hubops.vislumbrops.analysis

import android.content.Context
import com.hubops.vislumbrops.core.*
import java.io.File
import java.util.zip.ZipFile

/**
 * Auditor de dependências com snapshot CVE embutido em res/raw/cve_database.json.
 *
 * Fontes de versão, por ordem de confiança:
 *  1. META-INF/maven/<grupo>/<artefacto>/pom.properties (versão declarada pelo build);
 *  2. versão inferida do nome do ficheiro em lib/ e assets/ (heurística).
 *
 * Intervalos são [introduced, fixed): correspondência numérica, nunca por prefixo de texto.
 * Não requer rede; não é um SBOM nem uma base atualizada em tempo real.
 */
class DependencyAuditor(private val catalogProvider: () -> CveCatalog.Parsed) {

    constructor(context: Context) : this({ loadFromResources(context) })

    private val catalog: CveCatalog.Parsed by lazy(catalogProvider)

    fun audit(artifactPath: String): AnalysisResult {
        val entries = catalog.entries
        val findings = mutableListOf<Finding>()
        val seenInfo = HashSet<String>()
        var scanned = 0
        var confirmedVersions = 0

        try {
            ZipFile(File(artifactPath)).use { zip ->
                var entriesSeen = 0
                for (e in zip.entries()) {
                    if (entriesSeen++ >= 10_000) break
                    if (e.isDirectory) continue

                    val pom = POM_PROPERTIES.find(e.name)
                    if (pom != null) {
                        scanned++
                        val artifact = pom.groupValues[1].lowercase()
                        val matches = entries.filter { artifact in it.names }
                        if (matches.isEmpty() || e.size > POM_MAX_BYTES) continue
                        val text = zip.getInputStream(e).use { BoundedArchiveReader.read(it, POM_MAX_BYTES.toInt()) }
                            ?.toString(Charsets.UTF_8) ?: continue
                        val version = CveCatalog.versionFromPomProperties(text) ?: continue
                        confirmedVersions++
                        for (cve in matches) {
                            if (cve.isAffected(version)) {
                                findings += Finding(
                                    category = CheckCategory.DEPENDENCIES,
                                    severity = cve.severity,
                                    title = "${cve.cve}: ${cve.library} $version afetada",
                                    description = "${cve.description} Versão declarada em pom.properties (confiança alta). Referência: ${cve.reference}",
                                    location = e.name,
                                    evidence = "version=$version",
                                    cweId = "CWE-1104",
                                    remediationHint = "Atualizar ${cve.library} para uma versão corrigida (ver ${cve.reference})."
                                )
                            }
                        }
                        continue
                    }

                    val name = e.name.lowercase()
                    if (!name.contains("lib/") && !name.contains("assets/")) continue
                    scanned++

                    for (cve in entries) {
                        if (cve.names.none { name.contains(it) }) continue
                        val version = CveCatalog.extractVersion(name, cve.names)
                        if (version != null) {
                            if (cve.isAffected(version)) {
                                findings += Finding(
                                    category = CheckCategory.DEPENDENCIES,
                                    severity = cve.severity,
                                    title = "${cve.cve}: ${cve.library} potencialmente afetada",
                                    description = "${cve.description} Versão inferida do caminho: $version; confirmar com SBOM/lockfile. Referência: ${cve.reference}",
                                    location = e.name,
                                    evidence = "version≈$version",
                                    cweId = "CWE-1104",
                                    remediationHint = "Confirmar a versão e atualizar para uma versão corrigida."
                                )
                            }
                        } else if (seenInfo.add("${cve.library}|${e.name}")) {
                            findings += Finding(
                                category = CheckCategory.DEPENDENCIES,
                                severity = Severity.INFO,
                                title = "Dependência identificada sem versão verificável: ${cve.library}",
                                description = "O nome foi encontrado, mas o APK não expõe versão suficiente para afirmar vulnerabilidade.",
                                location = e.name,
                                cweId = "CWE-1104",
                                remediationHint = "Gerar SBOM a partir do lockfile/build e confirmar a versão real."
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            return AnalysisResult(
                checks = listOf(VerificationCheck(
                    "DEPS-ERROR", CheckCategory.DEPENDENCIES,
                    "Falha ao inspecionar dependências", CheckResult.ERROR)),
                findings = emptyList()
            )
        }

        val actionable = findings.count { it.severity != Severity.INFO }
        val result = when {
            entries.isEmpty() -> CheckResult.SKIPPED
            scanned == 0 -> CheckResult.SKIPPED
            findings.any { it.severity == Severity.CRITICAL } -> CheckResult.FAIL
            actionable > 0 -> CheckResult.WARN
            findings.isNotEmpty() -> CheckResult.WARN // só INFO: versões não verificáveis
            else -> CheckResult.PASS // nenhuma correspondência na base local (não prova ausência)
        }

        val rejected = if (catalog.rejected.isEmpty()) "" else "; ${catalog.rejected.size} entradas da base rejeitadas por validação"
        val description = if (entries.isEmpty()) "Base CVE local indisponível ou inválida; análise não executada"
        else "$scanned entradas verificadas ($confirmedVersions com versão confirmada por pom.properties); " +
            "$actionable correspondências; base local de ${entries.size} CVEs, não exaustiva$rejected"

        return AnalysisResult(listOf(VerificationCheck("DEPS-AUDIT", CheckCategory.DEPENDENCIES, description, result)), findings)
    }

    private companion object {
        val POM_PROPERTIES = Regex("^META-INF/maven/[^/]+/([^/]+)/pom\\.properties$")
        const val POM_MAX_BYTES = 16L * 1024

        fun loadFromResources(context: Context): CveCatalog.Parsed = try {
            val id = context.resources.getIdentifier("cve_database", "raw", context.packageName)
            val raw = context.resources.openRawResource(id).bufferedReader().use { it.readText() }
            CveCatalog.parse(raw)
        } catch (_: Exception) {
            CveCatalog.Parsed(emptyList(), emptyList())
        }
    }
}
