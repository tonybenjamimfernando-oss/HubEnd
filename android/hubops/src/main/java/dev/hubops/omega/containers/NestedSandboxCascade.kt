package dev.hubops.omega.containers

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * NSC capability planner and constrained proot launcher.
 * JVM mode is analysis-only: a ClassLoader is NOT a security sandbox.
 * PRoot is user-space path virtualization, NOT a kernel/container boundary.
 */
enum class IsolationLevel { JVM_STATIC_ANALYSIS, PROOT_USERSPACE, TERMUX_DELEGATED, UNAVAILABLE }

data class WorkloadProfile(
    val needsNetworkStack: Boolean = false,
    val needsFilesystemIsolation: Boolean = true,
    val requiresUnixBinary: Boolean = false,
    val expectedRamMb: Long = 256,
)

data class SandboxCapabilities(
    val availableRamMb: Long,
    val prootExecutable: File?,
    val rootfsDirectory: File?,
    val termuxAvailable: Boolean,
    val nativeLibraryDirectory: File? = null,
)

data class ExecutionPlan(
    val level: IsolationLevel,
    val ready: Boolean,
    val rationale: String,
    val warnings: List<String>,
)

data class SandboxExecutionResult(val exitCode: Int, val peakObservedRssMb: Long, val stdoutLog: File, val killedForMemory: Boolean, val timedOut: Boolean)

class NestedSandboxCascade(
    private val appPrivateRoot: File,
    private val capabilities: () -> SandboxCapabilities,
) {
    fun plan(profile: WorkloadProfile): ExecutionPlan {
        require(profile.expectedRamMb in 32..16_384) { "Limite de RAM esperado inválido." }
        val caps = capabilities()
        val proot = caps.prootExecutable?.let(::isSafeExecutable)
        val rootfs = caps.rootfsDirectory?.let(::isSafeRootfs)
        val prootDepsReady = caps.nativeLibraryDirectory?.let { libs ->
            File(libs, "libtalloc.so").isFile && File(appPrivateRoot, "native/libtalloc.so.2").isFile
        } == true
        if (profile.requiresUnixBinary) {
            if (proot != null && rootfs != null && prootDepsReady && caps.availableRamMb >= profile.expectedRamMb + 256) {
                return ExecutionPlan(IsolationLevel.PROOT_USERSPACE, true,
                    "Executável proot e rootfs privados detetados; RAM disponível suficiente.",
                    listOf("PRoot não é isolamento de kernel nem garante proteção contra escape.",
                        "Não são montados /system, /data, /proc ou /dev do host.",
                        "A rede não é isolada nem desativada por este launcher; o workload pode herdar a rede do host.",
                        "O limite de RAM observa o processo proot via /proc; não é um cgroup e não contabiliza com garantia todos os descendentes."))
            }
            if (caps.termuxAvailable) return ExecutionPlan(IsolationLevel.TERMUX_DELEGATED, false,
                "Termux detetado, mas execução delegada exige ação explícita e tarefa allowlisted.",
                listOf("Não é uma sandbox gerida pelo HubOps.", "Não enviar comandos arbitrários nem credenciais.",
                    "A disponibilidade do Termux não prova isolamento Unix adicional."))
            return ExecutionPlan(IsolationLevel.UNAVAILABLE, false,
                "Carga UNIX indisponível: proot executável/rootfs/RAM não confirmados e Termux não detetado.",
                listOf("Fallback para análise estática JVM; não executar o binário."))
        }
        return ExecutionPlan(IsolationLevel.JVM_STATIC_ANALYSIS, true,
            "Carga compatível com análise estática JVM; execução arbitrária não é suportada neste nível.",
            listOf("ClassLoader não constitui sandbox de segurança.", "Código não confiável não será carregado nem executado."))
    }

    /**
     * Launches only a binary inside the app-private rootfs. Callers must obtain user
     * approval before invocation. No host filesystem binds or shell interpolation.
     */
    fun runInProot(
        command: List<String>,
        workingDirectory: String = "/workspace",
        ramLimitMb: Long = 512,
        timeoutMs: Long = 120_000,
        approvedByUser: Boolean,
    ): Result<SandboxExecutionResult> = runCatching {
        require(approvedByUser) { "Execução requer aprovação explícita do utilizador." }
        require(command.isNotEmpty() && command.size <= 64 && command.all { it.isNotBlank() && '\u0000' !in it })
        require(command.first().startsWith("/") && !command.first().contains("..")) {
            "O executável deve ser um caminho absoluto dentro do rootfs."
        }
        require(workingDirectory.startsWith("/") && !workingDirectory.split('/').any { it == ".." })
        require(ramLimitMb in 64..4096 && timeoutMs in 1_000..600_000)
        val caps = capabilities()
        val proot = caps.prootExecutable?.let(::isSafeExecutable)
            ?: error("Binário proot ELF executável ausente; um ficheiro .so não é executável proot.")
        val rootfs = caps.rootfsDirectory?.let(::isSafeRootfs) ?: error("Rootfs privado ausente ou inseguro.")
        require(caps.nativeLibraryDirectory?.let { libs ->
            File(libs, "libtalloc.so").isFile && File(appPrivateRoot, "native/libtalloc.so.2").isFile
        } == true) { "Dependência libtalloc ausente no nativeLibraryDir/app-private native. Execute bootstrap primeiro." }
        require(caps.availableRamMb >= ramLimitMb + 128) { "RAM disponível insuficiente para o limite solicitado." }
        val resolvedExecutable = File(rootfs, command.first().removePrefix("/")).canonicalFile
        require(resolvedExecutable.path.startsWith(rootfs.canonicalPath + File.separator) && resolvedExecutable.isFile) {
            "Executável não existe dentro do rootfs privado."
        }
        val log = File(appPrivateRoot, "nsc-${System.nanoTime()}.log")
        val args = mutableListOf(proot.absolutePath, "-r", rootfs.absolutePath, "-w", workingDirectory,
            "--kill-on-exit", "--", command.first())
        args += command.drop(1)
        val process = ProcessBuilder(args).redirectErrorStream(true).redirectOutput(log).apply {
            caps.nativeLibraryDirectory?.let { environment()["LD_LIBRARY_PATH"] = "${it.absolutePath}:${File(appPrivateRoot, "native").absolutePath}" }
        }.start()
        val killedForMemory = AtomicBoolean(false)
        val timedOut = AtomicBoolean(false)
        var peak = 0L
        val started = System.nanoTime()
        // HubEnd: Process.pid() não existe em Android; sem PID não há limite de RAM -> falha fechada.
        val pid = dev.hubops.omega.ProcessIds.pidOrNull(process) ?: run {
            process.destroyForcibly()
            error("Monitorização de memória indisponível neste dispositivo; execução PRoot cancelada.")
        }
        while (process.isAlive) {
            val elapsed = (System.nanoTime() - started) / 1_000_000
            if (elapsed > timeoutMs) { timedOut.set(true); process.destroyForcibly(); break }
            val rss = readRssMb(pid)
            peak = maxOf(peak, rss)
            if (rss > ramLimitMb) {
                killedForMemory.set(true)
                process.destroyForcibly()
                break
            }
            Thread.sleep(250)
        }
        val exit = process.waitFor()
        SandboxExecutionResult(exit, peak, log, killedForMemory.get(), timedOut.get())
    }

    private fun isSafeExecutable(file: File): File? {
        if (!file.exists() || !file.isFile || !file.canExecute() || hasSymlinkInPath(file)) return null
        val canonical = file.canonicalFile
        val nativeDir = File(appPrivateRoot, "native").canonicalFile
        return canonical.takeIf { it.path.startsWith(nativeDir.path + File.separator) }
    }

    private fun isSafeRootfs(file: File): File? {
        if (!file.exists() || !file.isDirectory || hasSymlinkInPath(file)) return null
        val canonical = file.canonicalFile
        val root = File(appPrivateRoot, "rootfs").canonicalFile
        return canonical.takeIf { it.path.startsWith(root.path + File.separator) }
    }

    private fun hasSymlinkInPath(file: File): Boolean = runCatching {
        var current: File? = file.absoluteFile
        while (current != null) {
            if (java.nio.file.Files.isSymbolicLink(current.toPath())) return@runCatching true
            current = current.parentFile
        }
        false
    }.getOrDefault(true)

    private fun readRssMb(pid: Long): Long = runCatching {
        val status = File("/proc/$pid/status").readLines().firstOrNull { it.startsWith("VmRSS:") }
        status?.filter { it.isDigit() }?.toLongOrNull()?.div(1024) ?: 0L
    }.getOrDefault(0L)
}
