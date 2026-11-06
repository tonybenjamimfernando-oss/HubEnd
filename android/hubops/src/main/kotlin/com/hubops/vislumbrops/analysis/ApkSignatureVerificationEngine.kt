package com.hubops.vislumbrops.analysis

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.hubops.vislumbrops.core.CheckCategory
import com.hubops.vislumbrops.core.CheckResult
import com.hubops.vislumbrops.core.VerificationCheck
import java.io.File
import java.security.MessageDigest

/**
 * Independent APK signature inspection engine.
 * Delegates APK signature parsing/verification to Android's PackageManager parser;
 * it does not claim to identify the exact signing scheme (v1/v2/v3/v4).
 */
class ApkSignatureVerificationEngine(private val context: Context) {
    data class Result(
        val checks: List<VerificationCheck>,
        val packageName: String? = null,
        val signerSha256: List<String> = emptyList(),
        val signerCount: Int = 0,
        val multipleSigners: Boolean? = null,
        val errorCode: String? = null
    )

    fun verify(path: String): Result {
        val started = System.currentTimeMillis()
        val file = File(path)
        if (!file.isFile || !file.canRead() || file.length() <= 0L) {
            return Result(listOf(check("APK-SIGNATURE-INPUT", "APK inexistente, vazio ou ilegível", CheckResult.ERROR, started)), errorCode = "INPUT_INVALID")
        }
        return try {
            val info = loadPackageInfo(file)
                ?: return Result(listOf(check("APK-SIGNATURE-PARSE", "PackageManager não conseguiu interpretar o APK nem obter uma assinatura verificável", CheckResult.FAIL, started)), errorCode = "PACKAGE_PARSE_FAILED")

            val certificates = extractCertificates(info)
            val fingerprints = certificates.mapNotNull { certificate ->
                certificate.encoded?.let(::sha256)
            }.distinct().sorted()

            val signatureAvailable = fingerprints.isNotEmpty()
            val checks = buildList {
                add(check(
                    "APK-SIGNATURE-PLATFORM-VERIFY",
                    if (signatureAvailable) "PackageManager interpretou o APK e devolveu certificados de assinatura" else "APK interpretado, mas nenhum certificado de assinatura foi exposto pelo Android",
                    if (signatureAvailable) CheckResult.PASS else CheckResult.ERROR,
                    started
                ))
                add(check(
                    "APK-SIGNER-CERTIFICATE-FINGERPRINT",
                    if (signatureAvailable) "Fingerprint SHA-256 do(s) certificado(s) calculado(s)" else "Fingerprint indisponível; assinatura não pode ser atestada",
                    if (signatureAvailable) CheckResult.PASS else CheckResult.ERROR,
                    started
                ))
                if (Build.VERSION.SDK_INT < 28) {
                    add(check("APK-SIGNING-HISTORY", "Android anterior à API 28: histórico de rotação de certificados não é exposto por esta API", CheckResult.SKIPPED, started))
                }
            }
            Result(
                checks = checks,
                packageName = info.packageName,
                signerSha256 = fingerprints,
                signerCount = fingerprints.size,
                multipleSigners = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.hasMultipleSigners() else null,
                errorCode = if (signatureAvailable) null else "SIGNER_CERTIFICATE_UNAVAILABLE"
            )
        } catch (error: SecurityException) {
            Result(listOf(check("APK-SIGNATURE-VERIFY", "Android rejeitou a leitura/verificação da assinatura: ${error.javaClass.simpleName}", CheckResult.FAIL, started)), errorCode = "SIGNATURE_REJECTED")
        } catch (error: Exception) {
            Result(listOf(check("APK-SIGNATURE-VERIFY", "Falha na análise da assinatura: ${error.javaClass.simpleName}", CheckResult.ERROR, started)), errorCode = "VERIFICATION_ERROR")
        }
    }

    @Suppress("DEPRECATION")
    private fun loadPackageInfo(file: File): PackageInfo? {
        val flags = if (Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNATURES
        }
        return if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
        }
    }

    @Suppress("DEPRECATION")
    private fun extractCertificates(info: PackageInfo): List<java.security.cert.Certificate> {
        return if (Build.VERSION.SDK_INT >= 28) {
            val signing = info.signingInfo ?: return emptyList()
            val signatures = if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
            signatures.orEmpty().mapNotNull { signature ->
                runCatching {
                    java.security.cert.CertificateFactory.getInstance("X.509")
                        .generateCertificate(signature.toByteArray().inputStream())
                }.getOrNull()
            }
        } else {
            info.signatures.orEmpty().mapNotNull { signature ->
                runCatching {
                    java.security.cert.CertificateFactory.getInstance("X.509")
                        .generateCertificate(signature.toByteArray().inputStream())
                }.getOrNull()
            }
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun check(id: String, description: String, result: CheckResult, started: Long) =
        VerificationCheck(id, CheckCategory.ARTIFACT_INTEGRITY, description, result, System.currentTimeMillis() - started)
}
