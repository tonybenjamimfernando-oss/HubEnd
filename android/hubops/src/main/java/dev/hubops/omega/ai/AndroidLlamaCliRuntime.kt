package dev.hubops.omega.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/** Signed llama.cpp CLI adapter; serializes models, bounds output/time/RSS, and never invokes a shell. */
class AndroidLlamaCliRuntime(context: Context, private val executable: File,
    private val timeoutSeconds: Long = 90, private val maxRssMb: Long = 1536) : ShardInferenceRuntime {
    private val app = context.applicationContext
    private val gate = Mutex()
    init { require(timeoutSeconds in 5..600 && maxRssMb in 256..8192) }

    override suspend fun generate(model: ShardModel, prompt: String, maxTokens: Int,
                                  temperature: Float): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(maxTokens in 1..1024 && temperature.isFinite() && temperature in 0f..1f)
            require(prompt.isNotBlank() && prompt.length <= 1_000_000)
            gate.withLock {
                val nativeRoot = File(app.filesDir, "native").canonicalFile
                require(executable.isFile && executable.canExecute() && !java.nio.file.Files.isSymbolicLink(executable.toPath()))
                require(executable.canonicalFile.path.startsWith(nativeRoot.path + File.separator))
                require(model.file.isFile && !java.nio.file.Files.isSymbolicLink(model.file.toPath()))
                val nonce = System.nanoTime()
                val promptFile = File(app.cacheDir, "snse-$nonce.prompt")
                val log = File(app.cacheDir, "snse-$nonce.out")
                try {
                    promptFile.writeText(prompt, Charsets.UTF_8)
                    val command = listOf(executable.absolutePath, "-m", model.file.absolutePath,
                        "-f", promptFile.absolutePath, "-n", maxTokens.toString(), "--temp", temperature.toString(),
                        "--no-display-prompt", "--simple-io")
                    val process = ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log).apply {
                        environment()["LD_LIBRARY_PATH"] = "${app.applicationInfo.nativeLibraryDir}:${File(app.filesDir, "native").absolutePath}"
                        environment()["GGML_NUM_THREADS"] = maxOf(1, Runtime.getRuntime().availableProcessors() - 1).toString()
                    }.start()
                    var peakRss = 0L
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
                    // HubEnd: Process.pid() não existe em Android; sem PID não há limite de RSS -> falha fechada.
                    val pid = dev.hubops.omega.ProcessIds.pidOrNull(process) ?: run {
                        process.destroyForcibly(); error("Monitorização de memória indisponível neste dispositivo; llama.cpp não foi executado.")
                    }
                    try {
                        while (process.isAlive) {
                            if (System.nanoTime() > deadline) {
                                process.destroyForcibly(); error("llama.cpp excedeu o timeout de ${timeoutSeconds}s.")
                            }
                            val rss = readRssMb(pid)
                            if (rss > 0) peakRss = maxOf(peakRss, rss)
                            if (rss > maxRssMb) {
                                process.destroyForcibly(); error("llama.cpp excedeu o limite observado de ${maxRssMb} MB RSS.")
                            }
                            Thread.sleep(250)
                        }
                        require(process.waitFor() == 0) { "llama.cpp terminou com código ${process.exitValue()}." }
                        require(log.isFile && log.length() in 1..2_000_000) { "Saída llama.cpp vazia ou acima de 2 MB." }
                        log.readText(Charsets.UTF_8).trim()
                    } finally { if (process.isAlive) process.destroyForcibly() }
                } finally { promptFile.delete(); log.delete() }
            }
        }
    }

    private fun readRssMb(pid: Long): Long = runCatching {
        val line = File("/proc/$pid/status").useLines { lines -> lines.firstOrNull { it.startsWith("VmRSS:") } }
        line?.filter { it.isDigit() }?.toLongOrNull()?.div(1024) ?: 0L
    }.getOrDefault(0L)
}
