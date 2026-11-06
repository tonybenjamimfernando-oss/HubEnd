package com.hubops.vislumbrops.runtime

import com.hubops.vislumbrops.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Safe default runtime adapter.
 *
 * A mobile APK auditor must not silently use ADB to install, launch, or uninstall
 * the inspected APK on the user's connected device. A separately provisioned
 * emulator/lab adapter can replace this implementation in a future release.
 */
class SandboxRunner {
    suspend fun execute(@Suppress("UNUSED_PARAMETER") artifactPath: String): AnalysisResult = withContext(Dispatchers.IO) {
        AnalysisResult(
            checks = listOf(
                VerificationCheck(
                    "RUNTIME-LAB-UNAVAILABLE",
                    CheckCategory.RUNTIME_STABILITY,
                    "Sem laboratório isolado configurado; nenhum APK foi instalado ou executado",
                    CheckResult.SKIPPED
                )
            ),
            findings = emptyList()
        )
    }
}
