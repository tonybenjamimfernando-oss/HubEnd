package dev.hubops.data.terminal

import dev.hubops.core.model.CommandResult
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Terminal local limitado ao UID/sandbox Android da aplicação. Não concede root nem acesso
 * automático ao armazenamento partilhado; os comandos continuam a ser código arbitrário do utilizador.
 */
class ShellRunner(
    private val workDir: File,
    private val timeoutSec: Long = 15,
    private val maxOutputBytes: Int = 64 * 1024,
) {
    val workingDirectoryPath: String get() = workDir.absolutePath

    init {
        require(timeoutSec in 1..120) { "Tempo limite do terminal inválido." }
        require(maxOutputBytes in 1..1_048_576) { "Limite de saída inválido." }
    }

    fun run(command: String): CommandResult {
        val cmd = command.trim()
        require(cmd.isNotEmpty()) { "Comando vazio." }
        require(cmd.length <= 2000) { "Comando demasiado longo (máx. 2000)." }
        require(!cmd.contains('\u0000')) { "Comando inválido." }
        if (!workDir.exists() && !workDir.mkdirs()) error("Não foi possível preparar a pasta privada do terminal.")
        require(workDir.isDirectory && workDir.canRead() && workDir.canWrite()) {
            "A pasta de trabalho do terminal é inválida."
        }

        val process = ProcessBuilder("sh", "-c", cmd)
            .directory(workDir)
            .redirectErrorStream(true)
            .apply {
                environment().remove("LD_PRELOAD")
                environment().remove("LD_LIBRARY_PATH")
            }
            .start()
        process.outputStream.close() // Não deixa comandos à espera de entrada interativa invisível.

        val buffer = ByteArrayOutputStream(minOf(maxOutputBytes, 8192))
        val truncated = java.util.concurrent.atomic.AtomicBoolean(false)
        val reader = thread(name = "hubops-terminal-output", isDaemon = true) {
            try {
                process.inputStream.use { input ->
                    val chunk = ByteArray(4096)
                    while (true) {
                        val n = input.read(chunk)
                        if (n < 0) break
                        synchronized(buffer) {
                            val room = maxOutputBytes - buffer.size()
                            if (room > 0) buffer.write(chunk, 0, minOf(n, room))
                            if (n > room) truncated.set(true)
                        }
                    }
                }
            } catch (_: IOException) {
                // O processo pode fechar ou o timeout pode interromper o fluxo de saída.
            }
        }

        val finished = try {
            process.waitFor(timeoutSec, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            process.destroyForcibly()
            Thread.currentThread().interrupt()
            throw e
        }
        if (!finished) {
            process.destroyForcibly()
            process.waitFor(2, TimeUnit.SECONDS)
        }
        reader.join(1500)
        if (reader.isAlive) {
            truncated.set(true)
            process.inputStream.close()
            reader.join(500)
        }
        val output = synchronized(buffer) { buffer.toString("UTF-8") }

        return CommandResult(
            command = cmd,
            output = output,
            exitCode = if (finished) process.exitValue() else -1,
            timedOut = !finished,
            truncated = truncated.get(),
        )
    }
}
