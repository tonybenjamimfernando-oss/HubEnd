package dev.hubops.core.recovery

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class TransactionRecoveryEngineTest {
    @Test fun interruptedOperationIsQuarantinedAndReplayIsIdempotent() {
        val root = Files.createTempDirectory("hubops-recovery").toFile()
        try {
            val first = TransactionRecoveryEngine(root)
            val tx = first.begin("zip-transfer", "artifacts/source.zip", "projects/imported")
            first.transition(tx.id, RecoveryState.APPLYING)
            val restarted = TransactionRecoveryEngine(root)
            val report = restarted.recoverInterrupted()
            assertEquals(1, report.interrupted.size)
            assertEquals(RecoveryState.NEEDS_ATTENTION, report.interrupted.single().state)
            assertEquals(RecoveryState.NEEDS_ATTENTION, restarted.recoverInterrupted().interrupted.single().state)
        } finally { root.deleteRecursively() }
    }

    @Test fun committedTransactionIsNotReplayed() {
        val root = Files.createTempDirectory("hubops-recovery").toFile()
        try {
            val engine = TransactionRecoveryEngine(root)
            val tx = engine.begin("zip-transfer", "artifacts/source.zip", "projects/imported")
            engine.transition(tx.id, RecoveryState.APPLYING)
            engine.transition(tx.id, RecoveryState.VALIDATING)
            engine.transition(tx.id, RecoveryState.COMMITTING)
            engine.transition(tx.id, RecoveryState.COMMITTED)
            val report = engine.recoverInterrupted()
            assertEquals(0, report.interrupted.size)
            assertEquals(1, report.terminal)
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectsTraversalAndInvalidStateTransition() {
        val root = Files.createTempDirectory("hubops-recovery").toFile()
        try {
            val engine = TransactionRecoveryEngine(root)
            assertThrows(IllegalArgumentException::class.java) {
                engine.begin("zip-transfer", "../outside.zip", "projects/imported")
            }
            val tx = engine.begin("zip-transfer", "artifacts/source.zip", "projects/imported")
            assertThrows(IllegalArgumentException::class.java) { engine.transition(tx.id, RecoveryState.COMMITTED) }
        } finally { root.deleteRecursively() }
    }

    @Test fun checksumMismatchIsReportedAsCorruption() {
        val root = Files.createTempDirectory("hubops-recovery").toFile()
        try {
            val engine = TransactionRecoveryEngine(root)
            val tx = engine.begin("zip-transfer", "artifacts/source.zip", "projects/imported")
            val file = File(File(root, "transactions"), "${tx.id}.txn")
            file.appendText("\nstate=COMMITTED\n")
            val report = engine.recoverInterrupted()
            assertEquals(listOf(file.name), report.corrupted)
        } finally { root.deleteRecursively() }
    }
}
