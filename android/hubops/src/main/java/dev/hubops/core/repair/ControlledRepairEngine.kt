package dev.hubops.core.repair

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID

/** A repair is first prepared, then explicitly approved, applied atomically where supported, and reversible. */
enum class RepairState { PREPARED, APPLIED, ROLLED_BACK, NEEDS_ATTENTION }

data class RepairPlan(
    val id: String,
    val relativeTarget: String,
    val expectedCurrentSha256: String?,
    val replacementSha256: String,
    val planSha256: String,
    val approvalToken: String,
)

data class RepairResult(val plan: RepairPlan, val state: RepairState, val backupRelativePath: String?, val message: String)

/**
 * Applies one-file repairs only inside an explicitly supplied workspace root.
 * It never follows symbolic links, never overwrites an unexpected target, and keeps a verified backup.
 */
class ControlledRepairEngine(private val workspaceRoot: File, private val stateRoot: File) {
    private val plansDir = File(stateRoot, "controlled-repairs")
    private val backupsDir = File(plansDir, "backups")

    init {
        require(workspaceRoot.exists() && workspaceRoot.isDirectory) { "Workspace inexistente." }
        require(!Files.isSymbolicLink(workspaceRoot.toPath())) { "Workspace não pode ser link simbólico." }
        require(plansDir.exists() || plansDir.mkdirs())
        require(backupsDir.exists() || backupsDir.mkdirs())
        require(plansDir.canonicalFile.path.startsWith(stateRoot.canonicalFile.path + File.separator))
    }

    @Synchronized
    fun prepare(relativeTarget: String, replacement: File): RepairPlan {
        val target = safeTarget(relativeTarget, allowMissing = true)
        require(target.parentFile.isDirectory) { "A pasta-pai do destino deve existir; o motor não cria árvores de diretórios implicitamente." }
        require(replacement.isFile && !Files.isSymbolicLink(replacement.toPath())) { "Ficheiro de correção inválido." }
        val expected = if (target.exists()) sha256(target) else null
        val replacementHash = sha256(replacement)
        require(expected != replacementHash) { "O destino já contém o conteúdo proposto." }
        val id = UUID.randomUUID().toString()
        val canonical = listOf(id, relativeTarget, expected ?: "<absent>", replacementHash).joinToString("\n")
        val digest = sha256(canonical.toByteArray(Charsets.UTF_8))
        val token = UUID.randomUUID().toString()
        val plan = RepairPlan(id, relativeTarget, expected, replacementHash, digest, token)
        persist(plan, RepairState.PREPARED, null)
        return plan
    }

    @Synchronized
    fun approveAndApply(plan: RepairPlan, explicitApprovalToken: String, replacement: File): RepairResult {
        require(explicitApprovalToken == plan.approvalToken) { "Aprovação explícita inválida." }
        val stored = load(plan.id) ?: error("Plano não encontrado.")
        require(stored.first == plan && stored.second == RepairState.PREPARED) { "Plano alterado, já utilizado ou inválido." }
        val target = safeTarget(plan.relativeTarget, allowMissing = true)
        require((if (target.exists()) sha256(target) else null) == plan.expectedCurrentSha256) {
            "O destino mudou desde a preparação; é necessária nova inspeção e aprovação."
        }
        require(replacement.isFile && sha256(replacement) == plan.replacementSha256) { "O ficheiro de correção mudou desde a preparação." }
        val backup = File(backupsDir, "${plan.id}.bak")
        if (target.exists()) {
            Files.copy(target.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES)
            require(sha256(backup) == plan.expectedCurrentSha256) { "Backup não passou na verificação de integridade." }
        }
        val temp = File(target.parentFile, ".${target.name}.${plan.id}.repair-tmp")
        try {
            Files.copy(replacement.toPath(), temp.toPath())
            require(sha256(temp) == plan.replacementSha256) { "Cópia temporária divergente." }
            moveIntoPlace(temp, target)
            require(sha256(target) == plan.replacementSha256) { "Conteúdo final divergente após aplicação." }
            persist(plan, RepairState.APPLIED, if (backup.exists()) "backups/${backup.name}" else null)
            return RepairResult(plan, RepairState.APPLIED, if (backup.exists()) "backups/${backup.name}" else null,
                "Correção aplicada; hash final confirmado e backup preservado quando existia conteúdo anterior.")
        } catch (failure: Exception) {
            temp.delete()
            persist(plan, RepairState.NEEDS_ATTENTION, if (backup.exists()) "backups/${backup.name}" else null)
            throw failure
        }
    }

    @Synchronized
    fun rollback(planId: String): RepairResult {
        val (plan, state, backupPath) = loadFull(planId) ?: error("Plano não encontrado ou inválido.")
        require(state == RepairState.APPLIED) { "Só uma correção aplicada pode ser revertida." }
        val target = safeTarget(plan.relativeTarget, allowMissing = false)
        require(sha256(target) == plan.replacementSha256) { "Destino alterado após a correção; reversão automática bloqueada." }
        if (plan.expectedCurrentSha256 == null) {
            // Never delete user files as a rollback shortcut. A newly-created file needs explicit manual handling.
            persist(plan, RepairState.NEEDS_ATTENTION, backupPath)
            // HubEnd: recusa por pré-condição (IllegalArgumentException), coerente com as restantes recusas do motor.
            throw IllegalArgumentException("O destino não existia antes da correção; remoção não é automática. Revisão manual necessária.")
        }
        val backup = File(plansDir, backupPath ?: error("Backup ausente." )).canonicalFile
        require(backup.path.startsWith(backupsDir.canonicalPath + File.separator) && backup.isFile)
        require(sha256(backup) == plan.expectedCurrentSha256) { "Backup ausente ou corrompido." }
        val temp = File(target.parentFile, ".${target.name}.${plan.id}.rollback-tmp")
        Files.copy(backup.toPath(), temp.toPath())
        require(sha256(temp) == plan.expectedCurrentSha256)
        moveIntoPlace(temp, target)
        require(sha256(target) == plan.expectedCurrentSha256)
        persist(plan, RepairState.ROLLED_BACK, backupPath)
        return RepairResult(plan, RepairState.ROLLED_BACK, backupPath, "Estado anterior restaurado e SHA-256 confirmado.")
    }

    private fun safeTarget(relative: String, allowMissing: Boolean): File {
        require(relative.isNotBlank() && !relative.startsWith('/') && !relative.contains('\\') &&
            relative.split('/').all { it.isNotBlank() && it != "." && it != ".." }) { "Caminho relativo inválido." }
        var cursor = workspaceRoot.canonicalFile
        val parts = relative.split('/')
        parts.dropLast(1).forEach { part ->
            cursor = File(cursor, part)
            require(!Files.isSymbolicLink(cursor.toPath())) { "Caminho contém link simbólico." }
            if (cursor.exists()) require(cursor.isDirectory) { "Componente do destino não é diretório." }
        }
        val target = File(cursor, parts.last()).canonicalFile
        require(target.path.startsWith(workspaceRoot.canonicalPath + File.separator)) { "Destino fora do workspace." }
        require(!Files.isSymbolicLink(target.toPath())) { "Destino é link simbólico." }
        if (target.exists()) require(target.isFile) { "O destino existente não é um ficheiro regular." }
        if (!allowMissing) require(target.isFile)
        return target
    }

    private fun moveIntoPlace(temp: File, target: File) {
        try { Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
        catch (_: AtomicMoveNotSupportedException) { Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
    }

    private fun persist(plan: RepairPlan, state: RepairState, backup: String?) {
        val p = Properties().apply {
            setProperty("id", plan.id); setProperty("path", plan.relativeTarget)
            setProperty("expected", plan.expectedCurrentSha256 ?: "<absent>")
            setProperty("replacement", plan.replacementSha256); setProperty("planHash", plan.planSha256)
            setProperty("token", plan.approvalToken); setProperty("state", state.name)
            setProperty("backup", backup ?: "")
        }
        val payload = p.stringPropertyNames().sorted().joinToString("\n") { "$it=${p.getProperty(it)}" }
        p.setProperty("recordHash", sha256(payload.toByteArray(Charsets.UTF_8)))
        val file = File(plansDir, "${plan.id}.properties")
        val temp = File(plansDir, ".${plan.id}.tmp")
        temp.outputStream().use { p.store(it, "HubOps controlled repair") }
        moveIntoPlace(temp, file)
    }

    private fun load(id: String): Pair<RepairPlan, RepairState>? = loadFull(id)?.let { Triple(it.first, it.second, it.third) }?.let { it.first to it.second }
    private fun loadFull(id: String): Triple<RepairPlan, RepairState, String?>? = runCatching {
        require(id.matches(Regex("[A-Fa-f0-9-]{36}")))
        val file = File(plansDir, "$id.properties").canonicalFile
        require(file.path.startsWith(plansDir.canonicalPath + File.separator) && file.isFile)
        val p = Properties().apply { file.inputStream().use { load(it) } }
        val recordHash = p.getProperty("recordHash") ?: error("Hash de registo ausente")
        val payload = p.stringPropertyNames().filter { it != "recordHash" }.sorted().joinToString("\n") { "$it=${p.getProperty(it)}" }
        require(recordHash == sha256(payload.toByteArray(Charsets.UTF_8)))
        val expected = p.getProperty("expected")
        val plan = RepairPlan(p.getProperty("id"), p.getProperty("path"), expected.takeUnless { it == "<absent>" },
            p.getProperty("replacement"), p.getProperty("planHash"), p.getProperty("token"))
        val canonical = listOf(plan.id, plan.relativeTarget, plan.expectedCurrentSha256 ?: "<absent>", plan.replacementSha256).joinToString("\n")
        require(plan.id == id && plan.planSha256 == sha256(canonical.toByteArray(Charsets.UTF_8)))
        Triple(plan, RepairState.valueOf(p.getProperty("state")), p.getProperty("backup").takeIf { it.isNotBlank() })
    }.getOrNull()

    private fun sha256(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(8192)
        while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
