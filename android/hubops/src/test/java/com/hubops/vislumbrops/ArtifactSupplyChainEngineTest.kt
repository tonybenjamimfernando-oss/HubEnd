package com.hubops.vislumbrops

import com.hubops.vislumbrops.analysis.ArtifactSupplyChainEngine
import com.hubops.vislumbrops.core.CheckResult
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class ArtifactSupplyChainEngineTest {
    private fun makeZip(vararg entries: Pair<String, ByteArray>): File {
        val file = File.createTempFile("vislumbrops-", ".apk")
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    @Test fun inventoriesAndHashesEntriesWithoutExtraction() {
        val apk = makeZip(
            "AndroidManifest.xml" to byteArrayOf(1, 2, 3),
            "classes.dex" to "dex-test".toByteArray(),
            "lib/arm64-v8a/libsample.so" to byteArrayOf(4, 5, 6)
        )
        try {
            val audit = ArtifactSupplyChainEngine().audit(apk.absolutePath)
            assertNotNull(audit.manifest)
            assertEquals(CheckResult.PASS, audit.result.checks.single().result)
            assertEquals(3, audit.manifest!!.entryCount)
            assertEquals(1, audit.manifest.dexFiles.size)
            assertEquals(1, audit.manifest.nativeLibraries.size)
            assertTrue(audit.manifest.manifestSha256.matches(Regex("[0-9a-f]{64}")))
            assertTrue(audit.manifest.toJson().contains(audit.manifest.manifestSha256))
            assertTrue(audit.manifest.entries.all { it.sha256?.length == 64 })
        } finally { apk.delete() }
    }

    @Test fun rejectsArchivePathTraversal() {
        val apk = makeZip(
            "AndroidManifest.xml" to byteArrayOf(1),
            "classes.dex" to byteArrayOf(2),
            "../outside.txt" to "unsafe".toByteArray()
        )
        try {
            val audit = ArtifactSupplyChainEngine().audit(apk.absolutePath)
            assertEquals(CheckResult.FAIL, audit.result.checks.single().result)
            assertTrue(audit.result.findings.any { it.severity.name == "CRITICAL" })
            assertTrue(audit.manifest!!.entries.any { it.kind == "UNSAFE_PATH" && it.path.contains("..") })
        } finally { apk.delete() }
    }

    @Test fun refusesOversizedInput() {
        val apk = makeZip("classes.dex" to byteArrayOf(1))
        try {
            val audit = ArtifactSupplyChainEngine(
                ArtifactSupplyChainEngine.Limits(maxArchiveBytes = 1)
            ).audit(apk.absolutePath)
            assertEquals(CheckResult.ERROR, audit.result.checks.single().result)
            assertNull(audit.manifest)
        } finally { apk.delete() }
    }
    @Test fun `manifest JSON safely escapes unusual entry names and is deterministic`() {
        val apk = makeZip(
            "AndroidManifest.xml" to byteArrayOf(1),
            "classes.dex" to byteArrayOf(2),
            "assets/quote\"and\nnewline.txt" to byteArrayOf(3)
        )
        try {
            val engine = ArtifactSupplyChainEngine()
            val first = engine.audit(apk.absolutePath).manifest!!
            val second = engine.audit(apk.absolutePath).manifest!!
            Json.parseToJsonElement(first.toJson())
            assertEquals(first.manifestSha256, second.manifestSha256)
        } finally { apk.delete() }
    }

}
