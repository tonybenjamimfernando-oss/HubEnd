package dev.hubend.app

import org.junit.Test

/**
 * Negative control: deliberately fails with a unique marker so CI can verify
 * that the instrumentation failure is present in a captured log or XML report.
 */
class ControlCrashTest {
    @Test
    fun deliberateControlCrash() {
        throw RuntimeException("CONTROL_CRASH_MARKER_7F3A")
    }
}
