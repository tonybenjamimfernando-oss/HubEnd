package com.hubops.vislumbrops.runtime

import com.hubops.vislumbrops.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PerformanceProfiler {
    suspend fun profile(packageName: String): AnalysisResult = withContext(Dispatchers.IO) {
        AnalysisResult(
            checks = listOf(
                VerificationCheck(
                    "PERF-EXTERNAL-LAB",
                    CheckCategory.PERFORMANCE,
                    "Medição dinâmica requer um laboratório/emulador externo; não foi inferida",
                    CheckResult.SKIPPED
                )
            )
        )
    }
}
