package dev.hubops

import dev.hubops.data.terminal.ShellRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ShellRunnerTest {
    private fun runner(timeout: Long = 5, max: Int = 1024) =
        ShellRunner(File(System.getProperty("java.io.tmpdir"), "hubops-test"), timeout, max)

    @Test
    fun echo_returnsOutput() {
        val r = runner().run("echo ola")
        assertEquals(0, r.exitCode)
        assertEquals("ola", r.output.trim())
    }

    @Test
    fun failure_hasNonZeroExit() {
        assertTrue(runner().run("exit 3").exitCode == 3)
    }

    @Test
    fun timeout_isReported() {
        val r = runner(timeout = 1).run("sleep 10")
        assertTrue(r.timedOut)
    }

    @Test
    fun output_isTruncated() {
        val r = runner(max = 100).run("yes x | head -c 5000")
        assertTrue(r.truncated)
        assertTrue(r.output.length <= 100)
    }

    @Test
    fun commandWithLargeOutputIsCapped() {
        val result = runner(max = 100).run("yes z | head -c 10000")
        assertTrue(result.output.toByteArray(Charsets.UTF_8).size <= 100)
        assertTrue(result.truncated)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidTimeoutIsRejected() {
        ShellRunner(File(System.getProperty("java.io.tmpdir"), "hubops-test"), 0, 1024)
    }

    @Test(expected = IllegalArgumentException::class)
    fun blank_isRejected() {
        runner().run("   ")
    }
}
