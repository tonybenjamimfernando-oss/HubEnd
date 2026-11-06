package com.hubops.vislumbrops.analysis

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.hubops.vislumbrops.core.*

class PermissionAnalyzer(private val context: Context) {
    private data class DangerousPermission(val name: String, val severity: Severity, val reason: String)

    private val dangerous = listOf(
        DangerousPermission("android.permission.READ_SMS", Severity.CRITICAL, "Leitura de SMS — dado altamente sensível."),
        DangerousPermission("android.permission.RECEIVE_SMS", Severity.HIGH, "Receção/interceção de SMS."),
        DangerousPermission("android.permission.SEND_SMS", Severity.HIGH, "Envio de SMS — pode gerar custos."),
        DangerousPermission("android.permission.READ_CONTACTS", Severity.HIGH, "Leitura de contactos."),
        DangerousPermission("android.permission.WRITE_CONTACTS", Severity.HIGH, "Alteração de contactos."),
        DangerousPermission("android.permission.ACCESS_FINE_LOCATION", Severity.HIGH, "Localização precisa."),
        DangerousPermission("android.permission.ACCESS_BACKGROUND_LOCATION", Severity.CRITICAL, "Localização em segundo plano."),
        DangerousPermission("android.permission.CAMERA", Severity.MEDIUM, "Acesso à câmara."),
        DangerousPermission("android.permission.RECORD_AUDIO", Severity.HIGH, "Gravação de áudio."),
        DangerousPermission("android.permission.READ_CALL_LOG", Severity.CRITICAL, "Acesso ao histórico de chamadas."),
        DangerousPermission("android.permission.READ_PHONE_STATE", Severity.MEDIUM, "Acesso ao estado do telefone."),
        DangerousPermission("android.permission.SYSTEM_ALERT_WINDOW", Severity.HIGH, "Sobreposição sobre outras aplicações."),
        DangerousPermission("android.permission.REQUEST_INSTALL_PACKAGES", Severity.HIGH, "Pedido de instalação de pacotes."),
        DangerousPermission("android.permission.MANAGE_EXTERNAL_STORAGE", Severity.HIGH, "Gestão alargada do armazenamento.")
    )

    @Suppress("DEPRECATION")
    fun analyze(artifactPath: String): AnalysisResult {
        val info = try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageArchiveInfo(
                    artifactPath, PackageManager.PackageInfoFlags.of((PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES).toLong())
                )
            } else {
                context.packageManager.getPackageArchiveInfo(artifactPath, PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES)
            }
        } catch (_: Exception) { null }

        if (info == null) return AnalysisResult(
            checks = listOf(VerificationCheck(
                "PERM-MANIFEST-PARSE", CheckCategory.PERMISSIONS,
                "Não foi possível interpretar as permissões do manifesto APK", CheckResult.ERROR
            ))
        )
        val requested = info.requestedPermissions.orEmpty().toSet()

        val findings = dangerous.filter { it.name in requested }.map { permission ->
            Finding(
                category = CheckCategory.PERMISSIONS,
                severity = permission.severity,
                title = "Permissão sensível: ${permission.name.substringAfterLast('.')}",
                description = permission.reason,
                location = "AndroidManifest.xml",
                cweId = "CWE-250",
                remediationHint = "Justificar a necessidade, limitar o uso e remover se não for essencial."
            )
        }.toMutableList()
        val accessibilityServices = info.services.orEmpty().filter {
            it.permission == "android.permission.BIND_ACCESSIBILITY_SERVICE" && it.exported
        }
        accessibilityServices.forEach { service ->
            findings += Finding(
                category = CheckCategory.PERMISSIONS,
                severity = Severity.HIGH,
                title = "Serviço de acessibilidade declarado",
                description = "O serviço ${service.name} solicita BIND_ACCESSIBILITY_SERVICE; confirmar finalidade e consentimento explícito.",
                location = "AndroidManifest.xml",
                cweId = "CWE-250",
                remediationHint = "Manter apenas se for essencial, documentar a finalidade e limitar o tratamento de dados."
            )
        }

        val result = when {
            findings.any { it.severity == Severity.CRITICAL } -> CheckResult.FAIL
            findings.isNotEmpty() -> CheckResult.WARN
            else -> CheckResult.PASS
        }
        return AnalysisResult(
            checks = listOf(VerificationCheck(
                "PERM-ANALYZE", CheckCategory.PERMISSIONS,
                "${findings.size} permissões sensíveis identificadas pelo PackageManager", result
            )),
            findings = findings
        )
    }
}
