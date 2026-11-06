package dev.hubops

import dev.hubops.despertar.AttributionLedger
import dev.hubops.despertar.BuildArtifactParser
import dev.hubops.despertar.LicenseClass
import dev.hubops.despertar.LicensePolicy
import dev.hubops.despertar.RepositoryStudy
import dev.hubops.despertar.SimilarRepo
import dev.hubops.despertar.StaticCodeAudit
import dev.hubops.despertar.TermuxJobs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DespertarTest {
    @Test
    fun classify_licenses() {
        assertEquals(LicenseClass.PERMISSIVE, LicensePolicy.classify("MIT"))
        assertEquals(LicenseClass.PERMISSIVE, LicensePolicy.classify("apache-2.0"))
        assertEquals(LicenseClass.STRONG_COPYLEFT, LicensePolicy.classify("GPL-3.0-only"))
        assertEquals(LicenseClass.WEAK_COPYLEFT, LicensePolicy.classify("MPL-2.0"))
        assertEquals(LicenseClass.UNKNOWN, LicensePolicy.classify("NOASSERTION"))
        assertEquals(LicenseClass.UNKNOWN, LicensePolicy.classify(null))
    }

    @Test
    fun onlyPermissiveMayBeCopied() {
        assertTrue(LicensePolicy.mayCopyCode(LicenseClass.PERMISSIVE))
        assertFalse(LicensePolicy.mayCopyCode(LicenseClass.STRONG_COPYLEFT))
        assertFalse(LicensePolicy.mayCopyCode(LicenseClass.UNKNOWN))
    }

    @Test
    fun ledger_listsEveryEntry() {
        val md = AttributionLedger.render(
            "eu/proj",
            listOf(SimilarRepo("a/b", "https://github.com/a/b", "MIT", LicenseClass.PERMISSIVE, 10)),
            "2026-11-04",
        )
        assertTrue(md.contains("[a/b](https://github.com/a/b)"))
        assertTrue(md.contains("Permissiva"))
        assertTrue(md.contains("Nenhum código foi copiado"))
    }


    @Test
    fun studyReportContainsTechnicalMetadataAndSafetyNotice() {
        val study = RepositoryStudy(
            fullName = "owner/project", description = "Projeto de demonstração",
            url = "https://github.com/owner/project", defaultBranch = "main",
            language = "Kotlin", license = "MIT", licenseClass = LicenseClass.PERMISSIVE,
            stars = 120, forks = 12, openIssues = 3, archived = false,
            updatedAt = "2026-11-04T10:00:00Z", topics = listOf("android", "compose"),
            languages = mapOf("Kotlin" to 9000L, "Java" to 1000L),
            readmeExcerpt = "# Demo\nAplicação Android",
            rootFiles = listOf("README.md", "build.gradle.kts"),
            buildSystems = listOf("Gradle"),
            dependencyManifests = listOf("app/build.gradle.kts"),
            recentCommits = listOf("2026-11-04 — feat: add audit"),
            latestRelease = "v1.2.0",
            sourceFileCount = 42,
            testFileCount = 8,
            workflowCount = 2,
            securityPolicyFound = true,
        )
        val report = study.report()
        assertTrue(report.contains("owner/project"))
        assertTrue(report.contains("Kotlin (9000 bytes)"))
        assertTrue(report.contains("Aplicação Android"))
        assertTrue(report.contains("Ficheiros de código identificados: 42"))
        assertTrue(report.contains("Ficheiros de teste identificados: 8"))
        assertTrue(report.contains("Gradle"))
        assertTrue(report.contains("app/build.gradle.kts"))
        assertTrue(report.contains("v1.2.0"))
        assertTrue(report.contains("não constitui auditoria de segurança", ignoreCase = true))
    }

    @Test
    fun recognizesMoreCommonLicenseFamilies() {
        assertEquals(LicenseClass.WEAK_COPYLEFT, LicensePolicy.classify("MPL-1.1"))
        assertEquals(LicenseClass.STRONG_COPYLEFT, LicensePolicy.classify("AGPL-3.0-only"))
    }

    @Test
    fun termuxJob_validApk() {
        val job = TermuxJobs.build("jadx", "/storage/emulated/0/Download/meu.apk", "saida1")
        assertTrue(job.executable.endsWith("/bash"))
        assertTrue(job.args.last().contains("/jadx"))
        assertTrue(job.args.last().contains("/storage/emulated/0/Download/meu.apk"))
        assertTrue(job.args.last().contains("mkdir -p"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun termuxJob_rejectsTraversal() {
        TermuxJobs.build("jadx", "/storage/emulated/0/../data/x.apk", "o")
    }

    @Test(expected = IllegalArgumentException::class)
    fun termuxJob_rejectsUnknownTool() {
        TermuxJobs.build("rm", "/storage/emulated/0/a.apk", "o")
    }

    @Test
    fun staticAuditReportsConcreteHeuristicSignals() {
        val result = StaticCodeAudit.analyze(
            mapOf("MainActivity.kt" to "WebView.settings.setJavaScriptEnabled(true)\nRuntime.getRuntime().exec(cmd)"),
            listOf("src/main/MainActivity.kt", "src/test/MainActivityTest.kt"),
        )
        assertEquals(1, result.filesReviewed)
        assertTrue(result.findings.any { it.contains("WebView") })
        assertTrue(result.findings.any { it.contains("Execução de processos") })
    }

    @Test
    fun staticAuditReportsFileAndLineForEachSignal() {
        val result = StaticCodeAudit.analyze(
            mapOf("src/Main.kt" to "val safe = true\nWebView.settings.setJavaScriptEnabled(true)"),
            listOf("src/test/MainTest.kt"),
        )
        assertTrue(result.findings.any { it.startsWith("src/Main.kt:2") && it.contains("WebView") })
    }

    @Test
    fun staticAuditDoesNotJoinDifferentFilesToCreateTlsFinding() {
        val result = StaticCodeAudit.analyze(
            mapOf(
                "Trust.kt" to "class CustomX509TrustManager {}",
                "Other.kt" to "return true",
            ),
            listOf("src/test/SampleTest.kt"),
        )
        assertFalse(result.findings.any { it.contains("validação TLS permissiva") })
    }

    @Test
    fun staticAuditDoesNotClaimCleanWhenNoSourceWasFetched() {
        val result = StaticCodeAudit.analyze(emptyMap(), emptyList())
        assertEquals(0, result.filesReviewed)
        assertTrue(result.findings.any { it.contains("Não foi possível obter ficheiros") })
    }

    @Test
    fun termuxResult_successRequiresZeroExitCode() {
        val pending = dev.hubops.despertar.TermuxRunResult(0, -2, "", "", "", -2, 0L)
        val success = dev.hubops.despertar.TermuxRunResult(1, 0, "ok", "", "", -1, 1L)
        val failure = dev.hubops.despertar.TermuxRunResult(2, 17, "", "folder exists", "", -1, 2L)
        assertTrue(pending.isPending)
        assertFalse(pending.succeeded)
        assertTrue(success.succeeded)
        assertFalse(failure.succeeded)
    }

    @Test(expected = IllegalArgumentException::class)
    fun termuxJob_rejectsBadOutName() {
        TermuxJobs.build("apktool", "/storage/emulated/0/a.apk", "../x")
    }

    @Test(expected = IllegalArgumentException::class)
    fun termuxJob_rejectsDotDirectoryName() {
        TermuxJobs.build("jadx", "/storage/emulated/0/a.apk", "..")
    }
    @Test
    fun apkArchiveInspectorCountsRealApkStructureWithoutExtracting() {
        val bytes = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                listOf("AndroidManifest.xml", "classes.dex", "classes2.dex", "resources.arsc", "res/layout/main.xml", "lib/arm64-v8a/libsample.so", "assets/config.json", "META-INF/CERT.RSA").forEach { name ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write("data".toByteArray())
                    zip.closeEntry()
                }
            }
        }.toByteArray()
        val report = dev.hubops.despertar.ApkArchiveInspector.inspect(ByteArrayInputStream(bytes))
        assertTrue(report.hasManifest)
        assertTrue(report.hasResourcesTable)
        assertEquals(2, report.dexFiles.size)
        assertEquals(1, report.nativeLibraries)
        assertEquals(1, report.resourceFiles)
        assertEquals(1, report.assetFiles)
        assertEquals(1, report.signatureFiles.size)
        assertFalse(report.truncated)
        assertTrue(report.asText().contains("não prova que o APK é seguro"))
    }

    @Test
    fun termuxDiagnosticAndOutputJobsAreReadOnlyAndValidated() {
        val diagnostic = TermuxJobs.environmentCheck()
        assertTrue(diagnostic.label.contains("Diagnóstico"))
        assertTrue(diagnostic.args.last().contains("command -v"))
        val output = TermuxJobs.inspectOutput("analise_01")
        assertTrue(output.args.last().contains("find"))
        assertTrue(output.args.last().contains("du -sh"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun termuxOutputInspectionRejectsShellInjection() {
        TermuxJobs.inspectOutput("saida;rm")
    }

    @Test
    fun androidBuildJobValidatesInputsAndProducesBoundedBuildScript() {
        val job = TermuxJobs.buildAndroidProject(
            "/storage/emulated/0/Download/HubOps/Projects/hubops-demo.zip",
            "hubops-demo-123",
            "a".repeat(64),
        )
        val script = job.args.last()
        assertTrue(job.label.contains("Compilar projeto Android"))
        assertTrue(script.contains("Gradle 8.11.1–8.14.3"))
        assertTrue(script.contains(":app:assembleDebug"))
        assertTrue(script.contains("--max-workers=2"))
        assertTrue(script.contains("Download/HubOps/Builds"))
        assertTrue(script.contains("ZIP contém caminhos inseguros"))
        assertTrue(script.contains("EXPECTED_SHA256"))
        assertTrue(script.contains("SHA-256 do ZIP não corresponde"))
        assertTrue(script.contains("não será sobrescrito"))
        assertTrue(script.contains("apksigner\" verify --verbose --print-certs"))
        assertTrue(script.contains("aapt\" dump badging"))
        assertTrue(script.contains("APK inválido: teste de integridade ZIP falhou"))
        assertTrue(script.contains("Package do APK não corresponde ao projeto"))
        assertTrue(script.contains("BUILD_OK"))
        assertTrue(script.contains("-build-report.txt"))
    }


    @Test
    fun buildArtifactParserRequiresSuccessfulCallbackAndCompleteReport() {
        val output = """BUILD_OK
APK=/storage/emulated/0/Download/HubOps/Builds/demo-123-debug.apk
BYTES=255
SHA256=${"a".repeat(64)}
PACKAGE=com.example.demo
REPORT=/storage/emulated/0/Download/HubOps/Builds/demo-123-build-report.txt
"""
        val success = dev.hubops.despertar.TermuxRunResult(7, 0, output, "", "", -1, 100L)
        assertEquals("/storage/emulated/0/Download/HubOps/Builds/demo-123-debug.apk", BuildArtifactParser.parse(success))

        val failed = success.copy(exitCode = 1)
        assertEquals(null, BuildArtifactParser.parse(failed))
        val missingReport = success.copy(stdout = output.replace(Regex("(?m)^REPORT=.*\\n"), ""))
        assertEquals(null, BuildArtifactParser.parse(missingReport))
        val mismatchedReport = success.copy(stdout = output.replace("demo-123-build-report", "other-build-report"))
        assertEquals(null, BuildArtifactParser.parse(mismatchedReport))
        val invalidHash = success.copy(stdout = output.replace("a".repeat(64), "invalid"))
        assertEquals(null, BuildArtifactParser.parse(invalidHash))
    }

    @Test(expected = IllegalArgumentException::class)
    fun androidBuildJobRejectsPathTraversal() {
        TermuxJobs.buildAndroidProject(
            "/storage/emulated/0/Download/HubOps/Projects/../../malicioso.zip",
            "hubops-demo-123",
            "a".repeat(64),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun androidBuildJobRejectsUnsafeProjectKey() {
        TermuxJobs.buildAndroidProject(
            "/storage/emulated/0/Download/HubOps/Projects/hubops-demo.zip",
            "demo;touch-pwned",
            "a".repeat(64),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun androidBuildJobRejectsInvalidExpectedHash() {
        TermuxJobs.buildAndroidProject(
            "/storage/emulated/0/Download/HubOps/Projects/hubops-demo.zip",
            "hubops-demo-123",
            "not-a-sha256",
        )
    }

}
