package com.hubops.vislumbrops

import com.hubops.vislumbrops.analysis.CveCatalog
import com.hubops.vislumbrops.analysis.CveEntry
import com.hubops.vislumbrops.analysis.VersionComparator
import com.hubops.vislumbrops.analysis.VersionRange
import com.hubops.vislumbrops.core.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CveCatalogTest {

    private val shipped: List<CveEntry> by lazy {
        val parsed = CveCatalog.parse(shippedJson())
        assertTrue("entradas rejeitadas: ${parsed.rejected}", parsed.rejected.isEmpty())
        parsed.entries
    }

    /**
     * HubEnd: na JVM lê o ficheiro do código-fonte; em ART (teste no dispositivo) lê o mesmo
     * recurso empacotado R.raw.cve_database, que é o que a aplicação usa em produção.
     */
    private fun shippedJson(): String {
        val source = File("src/main/res/raw/cve_database.json")
        if (source.isFile) return source.readText()
        val registry = Class.forName("androidx.test.platform.app.InstrumentationRegistry")
        val instrumentation = registry.getMethod("getInstrumentation").invoke(null) as android.app.Instrumentation
        return instrumentation.targetContext.resources.openRawResource(dev.hubops.R.raw.cve_database)
            .bufferedReader().use { it.readText() }
    }

    private fun entry(cve: String, version: String) =
        shipped.filter { it.cve == cve }.also { assertTrue("$cve ausente da base", it.isNotEmpty()) }
            .any { it.isAffected(version) }

    @Test fun comparaNumericamenteSemPrefixoDeTexto() {
        val a = VersionComparator.parse("4.10")!!
        val b = VersionComparator.parse("4.9.2")!!
        assertTrue(VersionComparator.compare(a, b) > 0)
        assertEquals(0, VersionComparator.compare(VersionComparator.parse("2.1")!!, VersionComparator.parse("2.1.0")!!))
        assertNull(VersionComparator.parse("beta"))
        assertEquals(listOf(2L, 15L, 0L), VersionComparator.parse("2.15.0-rc1"))
        assertEquals(listOf(1L, 0L, 1L, 7L), VersionComparator.parse("1.0.1g")) // sufixo de letra (OpenSSL)
    }

    @Test fun intervaloEhSemiaberto() {
        val r = VersionRange("2.0", "2.15.0")
        assertTrue(r.contains("2.0"))
        assertTrue(r.contains("2.14.1"))
        assertFalse(r.contains("2.15.0"))
        assertFalse(r.contains("1.9.9"))
    }

    @Test fun intervaloInvalidoEhRejeitado() {
        val bad = """[{"library":"x","cve":"CVE-2020-0001","severity":"HIGH","description":"d","ranges":[{"fixed":"abc"}]}]"""
        val parsed = CveCatalog.parse(bad)
        assertTrue(parsed.entries.isEmpty())
        assertEquals(1, parsed.rejected.size)
    }

    @Test fun prefixoLegadoRespeitaFronteira() {
        val json = """[{"library":"okhttp","cve":"CVE-2020-0002","severity":"LOW","description":"d","affected":["4.1"]}]"""
        val e = CveCatalog.parse(json).entries.single()
        assertTrue(e.isAffected("4.1"))
        assertTrue(e.isAffected("4.1.3"))
        assertFalse(e.isAffected("4.10.0")) // o prefixo textual antigo marcava isto como afetado
    }

    @Test fun log4jCobreTodaALinhaAfetada() {
        assertTrue(entry("CVE-2021-44228", "2.3.0"))   // falso negativo da lógica por prefixo
        assertTrue(entry("CVE-2021-44228", "2.14.1"))
        assertFalse(entry("CVE-2021-44228", "2.15.0"))
        assertFalse(entry("CVE-2021-44228", "1.2.17"))
    }

    @Test fun heartbleedSoAfetaOpenssl101aF() {
        assertTrue(entry("CVE-2014-0160", "1.0.1"))
        assertFalse(entry("CVE-2014-0160", "1.1.0")) // a base antiga marcava 1.1.0 por engano
        assertFalse(entry("CVE-2014-0160", "1.0.1g"))
    }

    @Test fun commonsCollectionsTemDuasLinhas() {
        assertTrue(entry("CVE-2015-6420", "3.2.1"))
        assertFalse(entry("CVE-2015-6420", "3.2.2"))
        assertTrue(entry("CVE-2015-6420", "4.0"))
        assertFalse(entry("CVE-2015-6420", "4.1"))
    }

    @Test fun extraiVersaoDoNomeEDoPom() {
        assertEquals("2.14.1", CveCatalog.extractVersion("assets/log4j-core-2.14.1.jar", listOf("log4j-core", "log4j")))
        assertNull(CveCatalog.extractVersion("lib/arm64-v8a/libcrypto.so", listOf("libcrypto")))
        assertEquals("2.9.10.2", CveCatalog.versionFromPomProperties("#x\ngroupId=a\nversion=2.9.10.2\n"))
        assertNull(CveCatalog.versionFromPomProperties("version=\${project.version}"))
    }

    @Test fun baseEnviadaEhValidaESemDuplicados() {
        assertTrue(shipped.size >= 10)
        assertEquals(shipped.size, shipped.map { it.cve to it.library }.toSet().size)
        assertTrue(shipped.any { it.severity == Severity.CRITICAL })
    }
}
