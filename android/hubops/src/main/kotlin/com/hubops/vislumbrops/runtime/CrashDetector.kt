package com.hubops.vislumbrops.runtime

class CrashDetector {
    fun detectCrashes(logcat: String): List<String> =
        logcat.split("\n")
            .filter { it.contains("FATAL EXCEPTION") || it.contains("AndroidRuntime: ") }
            .chunked(6).map { it.joinToString("\n") }

    fun detectAnrs(logcat: String): List<String> =
        logcat.split("\n")
            .filter { it.contains("ANR in") || it.contains("Application Not Responding") }
}
