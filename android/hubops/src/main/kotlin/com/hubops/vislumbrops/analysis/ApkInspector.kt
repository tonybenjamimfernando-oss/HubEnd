package com.hubops.vislumbrops.analysis

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.hubops.vislumbrops.core.*
import java.io.File
import java.util.zip.ZipFile

class ApkInspector(private val context: Context) {
    fun verifyIntegrity(artifactPath: String): List<VerificationCheck> {
        val file = File(artifactPath)
        val checks = mutableListOf<VerificationCheck>()
        checks += measure("APK-EXISTS", CheckCategory.ARTIFACT_INTEGRITY, "Artefacto existe e é legível") {
            if (file.isFile && file.canRead()) CheckResult.PASS else CheckResult.FAIL
        }
        checks += measure("APK-SIZE", CheckCategory.ARTIFACT_INTEGRITY, "Tamanho máximo de 100 MB") {
            when {
                !file.isFile || file.length() <= 0L -> CheckResult.FAIL
                file.length() > 100L * 1024 * 1024 -> CheckResult.WARN
                else -> CheckResult.PASS
            }
        }
        checks += measure("APK-ZIP-VALID", CheckCategory.ARTIFACT_INTEGRITY,
            "Arquivo ZIP contém AndroidManifest.xml e classes.dex") {
            try {
                ZipFile(file).use { zip ->
                    val names = zip.entries().asSequence().take(10_000).map { it.name }.toSet()
                    if ("AndroidManifest.xml" in names && "classes.dex" in names) CheckResult.PASS else CheckResult.FAIL
                }
            } catch (_: Exception) { CheckResult.ERROR }
        }
        checks += measure("APK-V1-SIGNATURE-METADATA", CheckCategory.ARTIFACT_INTEGRITY,
            "Metadados de assinatura v1 presentes (não valida a assinatura)") {
            try {
                ZipFile(file).use { zip ->
                    val found = zip.entries().asSequence().take(10_000).any {
                        it.name.startsWith("META-INF/") &&
                            (it.name.endsWith(".RSA", true) || it.name.endsWith(".DSA", true) || it.name.endsWith(".EC", true))
                    }
                    if (found) CheckResult.WARN else CheckResult.SKIPPED
                }
            } catch (_: Exception) { CheckResult.ERROR }
        }
        val info = readPackageInfo(artifactPath)
        checks += VerificationCheck(
            "APK-MANIFEST-PARSE", CheckCategory.ARTIFACT_INTEGRITY,
            if (info != null) "Manifesto interpretado pelo PackageManager" else "PackageManager não interpretou o manifesto binário",
            if (info != null) CheckResult.PASS else CheckResult.ERROR
        )
        val app = info?.applicationInfo
        checks += VerificationCheck(
            "APK-DEBUGGABLE", CheckCategory.COMPLIANCE, "Flag debuggable interpretada do manifesto",
            when {
                app == null -> CheckResult.ERROR
                app.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 -> CheckResult.WARN
                else -> CheckResult.PASS
            }
        )
        checks += VerificationCheck(
            "APK-ALLOW-BACKUP", CheckCategory.COMPLIANCE, "Flag allowBackup interpretada do manifesto",
            when {
                app == null -> CheckResult.ERROR
                app.flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP != 0 -> CheckResult.WARN
                else -> CheckResult.PASS
            }
        )
        return checks
    }

    @Suppress("DEPRECATION")
    fun readPackageInfo(path: String): PackageInfo? = try {
        if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(
                (PackageManager.GET_PERMISSIONS or PackageManager.GET_META_DATA).toLong()
            ))
        } else {
            context.packageManager.getPackageArchiveInfo(path, PackageManager.GET_PERMISSIONS or PackageManager.GET_META_DATA)
        }
    } catch (_: Exception) { null }

    private inline fun measure(id: String, cat: CheckCategory, desc: String, block: () -> CheckResult): VerificationCheck {
        val start = System.currentTimeMillis()
        val result = try { block() } catch (_: Exception) { CheckResult.ERROR }
        return VerificationCheck(id, cat, desc, result, System.currentTimeMillis() - start)
    }
}
