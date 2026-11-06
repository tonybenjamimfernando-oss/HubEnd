package dev.hubops.core.recovery

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID

/** Durable, fail-closed journal for operations that may be interrupted by process death. */
enum class RecoveryState {
    PREPARED, APPLYING, VALIDATING, COMMITTING, COMMITTED,
    ROLLING_BACK, ROLLED_BACK, NEEDS_ATTENTION
}

data class RecoveryTransaction(
    val id: String,
    val operation: String,
    val source: String,
    val destination: String,
    val state: RecoveryState,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val detail: String,
)

data class RecoveryReport(
    val inspected: Int,
    val interrupted: List<RecoveryTransaction>,
    val corrupted: List<String>,
    val terminal: Int,
)

/**
 * Independent recovery engine. It records intent and progress but never deletes, overwrites,
 * promotes, or rolls back user data automatically. Ambiguous/interrupted work is quarantined
 * as NEEDS_ATTENTION for explicit inspection by the owning engine or user.
 */
class TransactionRecoveryEngine(private val root: File) {
    private val journal = File(root, "transactions")

    init {
        require(root.exists() || root.mkdirs()) { "Não foi possível criar a raiz de recuperação." }
        require(journal.exists() || journal.mkdirs()) { "Não foi possível criar o diário transacional." }
        require(journal.canonicalFile.path.startsWith(root.canonicalFile.path + File.separator))
    }

    @Synchronized
    fun begin(operation: String, source: String, destination: String, detail: String = ""): RecoveryTransaction {
        require(operation.matches(Regex("[a-zA-Z0-9._-]{1,80}")))
        validateRelative(source); validateRelative(destination)
        val now = System.currentTimeMillis()
        val tx = RecoveryTransaction(UUID.randomUUID().toString(), operation, source, destination,
            RecoveryState.PREPARED, now, now, detail.take(2_000))
        persist(tx)
        return tx
    }

    @Synchronized
    fun transition(id: String, next: RecoveryState, detail: String = ""): RecoveryTransaction {
        val current = read(id) ?: error("Transação inexistente ou diário inválido.")
        require(allowed(current.state, next)) { "Transição inválida: ${current.state} -> $next" }
        val updated = current.copy(state = next, updatedAtEpochMs = System.currentTimeMillis(), detail = detail.take(2_000))
        persist(updated)
        return updated
    }

    /** On startup, non-terminal transactions are marked for review; no filesystem mutation occurs. */
    @Synchronized
    fun recoverInterrupted(): RecoveryReport {
        val interrupted = mutableListOf<RecoveryTransaction>()
        val corrupted = mutableListOf<String>()
        var inspected = 0
        var terminal = 0
        journal.listFiles { f -> f.isFile && f.extension == "txn" }.orEmpty().sortedBy { it.name }.forEach { file ->
            inspected++
            val tx = read(file.nameWithoutExtension)
            if (tx == null) {
                corrupted += file.name
            } else if (tx.state in TERMINAL) {
                terminal++
            } else {
                val attention = if (tx.state == RecoveryState.NEEDS_ATTENTION) tx else
                    tx.copy(state = RecoveryState.NEEDS_ATTENTION, updatedAtEpochMs = System.currentTimeMillis(),
                        detail = "Interrupção detetada na inicialização; requer reconciliação explícita. Estado anterior=${tx.state}. ${tx.detail}".take(2_000))
                if (attention != tx) persist(attention)
                interrupted += attention
            }
        }
        return RecoveryReport(inspected, interrupted, corrupted, terminal)
    }

    @Synchronized
    fun transactions(limit: Int = 100): List<RecoveryTransaction> = journal.listFiles { f -> f.isFile && f.extension == "txn" }
        .orEmpty().sortedByDescending { it.lastModified() }.take(limit.coerceIn(1, 500))
        .mapNotNull { read(it.nameWithoutExtension) }

    private fun persist(tx: RecoveryTransaction) {
        val target = transactionFile(tx.id)
        val props = Properties().apply {
            setProperty("id", tx.id); setProperty("operation", tx.operation)
            setProperty("source", tx.source); setProperty("destination", tx.destination)
            setProperty("state", tx.state.name); setProperty("created", tx.createdAtEpochMs.toString())
            setProperty("updated", tx.updatedAtEpochMs.toString()); setProperty("detail", tx.detail)
        }
        val payload = props.toPayload()
        props.setProperty("sha256", sha256(payload))
        val temp = File(journal, ".${tx.id}.${System.nanoTime()}.tmp")
        try {
            temp.outputStream().buffered().use { props.store(it, "HubOps transaction journal") }
            try { Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        } finally { temp.delete() }
    }

    private fun read(id: String): RecoveryTransaction? = runCatching {
        val file = transactionFile(id)
        require(file.isFile)
        val p = Properties().apply { file.inputStream().buffered().use { load(it) } }
        val expected = p.getProperty("sha256") ?: error("checksum ausente")
        require(expected == sha256(p.toPayload())) { "checksum divergente" }
        val tx = RecoveryTransaction(p.getProperty("id"), p.getProperty("operation"), p.getProperty("source"),
            p.getProperty("destination"), RecoveryState.valueOf(p.getProperty("state")),
            p.getProperty("created").toLong(), p.getProperty("updated").toLong(), p.getProperty("detail", ""))
        require(tx.id == id && tx.id.matches(Regex("[A-Fa-f0-9-]{36}")))
        validateRelative(tx.source); validateRelative(tx.destination)
        tx
    }.getOrNull()

    private fun transactionFile(id: String): File {
        require(id.matches(Regex("[A-Fa-f0-9-]{36}")))
        val file = File(journal, "$id.txn").canonicalFile
        require(file.path.startsWith(journal.canonicalPath + File.separator))
        return file
    }

    private fun validateRelative(value: String) {
        require(value.isNotBlank() && !value.startsWith('/') && !value.contains('\\') && value.none { it.isISOControl() } &&
            value.split('/').all { it.isNotBlank() && it != "." && it != ".." && !it.startsWith(".") }) {
            "O diário aceita apenas caminhos relativos seguros."
        }
    }

    private fun allowed(from: RecoveryState, to: RecoveryState): Boolean = when (from) {
        RecoveryState.PREPARED -> to in setOf(RecoveryState.APPLYING, RecoveryState.ROLLED_BACK, RecoveryState.NEEDS_ATTENTION)
        RecoveryState.APPLYING -> to in setOf(RecoveryState.VALIDATING, RecoveryState.ROLLING_BACK, RecoveryState.NEEDS_ATTENTION)
        RecoveryState.VALIDATING -> to in setOf(RecoveryState.COMMITTING, RecoveryState.ROLLING_BACK, RecoveryState.NEEDS_ATTENTION)
        RecoveryState.COMMITTING -> to in setOf(RecoveryState.COMMITTED, RecoveryState.ROLLING_BACK, RecoveryState.NEEDS_ATTENTION)
        RecoveryState.ROLLING_BACK -> to in setOf(RecoveryState.ROLLED_BACK, RecoveryState.NEEDS_ATTENTION)
        RecoveryState.NEEDS_ATTENTION -> to in setOf(RecoveryState.ROLLING_BACK, RecoveryState.COMMITTING, RecoveryState.ROLLED_BACK, RecoveryState.COMMITTED)
        RecoveryState.COMMITTED, RecoveryState.ROLLED_BACK -> false
    }

    private fun Properties.toPayload(): String = stringPropertyNames().filter { it != "sha256" }.sorted()
        .joinToString("\n") { "$it=${getProperty(it)}" }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    companion object { private val TERMINAL = setOf(RecoveryState.COMMITTED, RecoveryState.ROLLED_BACK) }
}
