package dev.hubops.core.integration

import com.hubops.vislumbrops.core.VerificationStatus
import dev.hubops.core.repair.RepairPlan
import dev.hubops.core.repair.RepairResult
import dev.hubops.core.repair.RepairState
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Base64
import java.util.Properties

class ProfessionalWorkflowCoordinatorTest {
    private val hash = "a".repeat(64)

    @Test fun completeWorkflowRequiresAssessmentApprovalAndReaudit() {
        val root = Files.createTempDirectory("hubops-pro").toFile()
        val coordinator = ProfessionalWorkflowCoordinator(root)
        val started = coordinator.begin("build-001", "source.zip", hash, "projects/demo")
        assertEquals(WorkflowPhase.SOURCE_ANALYZED, started.phase)
        val assessed = coordinator.recordAssessment(started.id, hash, "verification-1", VerificationStatus.PASSED, false, "e".repeat(64))
        assertEquals(WorkflowPhase.ASSESSED, assessed.phase)
        val plan = RepairPlan("plan-1", "src/Main.kt", hash, "b".repeat(64), hash, "approval")
        val prepared = coordinator.recordRepairPrepared(assessed.id, plan)
        assertEquals(WorkflowPhase.REPAIR_PREPARED, prepared.phase)
        val applied = coordinator.recordRepairApplied(prepared.id, RepairResult(plan, RepairState.APPLIED, "backups/plan-1.bak", "ok"))
        assertEquals(WorkflowPhase.REPAIR_APPLIED, applied.phase)
        val complete = coordinator.recordReaudit(applied.id, "verification-2", VerificationStatus.PASSED, "c".repeat(64), false, "source-v2.zip", "d".repeat(64), "build-001-r2", "f".repeat(64))
        assertEquals(WorkflowPhase.REAUDIT_COMPLETE, complete.phase)
        assertEquals(complete.id, coordinator.latest()?.id)
        assertTrue(complete.eventCount >= 5)
    }

    @Test fun incompleteAuditCannotCloseWorkflow() {
        val root = Files.createTempDirectory("hubops-pro-review").toFile()
        val coordinator = ProfessionalWorkflowCoordinator(root)
        val started = coordinator.begin("build-002", "source.zip", hash, "projects/demo")
        val reviewed = coordinator.recordAssessment(started.id, hash, "verification-1", VerificationStatus.PASSED_WITH_WARNINGS, true, "e".repeat(64))
        assertEquals(WorkflowPhase.NEEDS_REVIEW, reviewed.phase)
        val prepared = coordinator.recordRepairPrepared(reviewed.id,
            RepairPlan("plan-2", "src/Main.kt", hash, "b".repeat(64), hash, "approval"))
        val invalidated = coordinator.invalidatePreparedRepair(prepared.id, "correction file unavailable")
        assertEquals(WorkflowPhase.NEEDS_REVIEW, invalidated.phase)
        assertNull(invalidated.repairPlanId)
    }

    @Test fun rollbackReturnsCaseToReview() {
        val root = Files.createTempDirectory("hubops-pro-rollback").toFile()
        val coordinator = ProfessionalWorkflowCoordinator(root)
        val started = coordinator.begin("build-rollback", "source.zip", hash, "projects/demo")
        val assessed = coordinator.recordAssessment(started.id, hash, "verification-1", VerificationStatus.PASSED, false, "e".repeat(64))
        val plan = RepairPlan("plan-rollback", "projects/demo/Main.kt", hash, "b".repeat(64), hash, "approval")
        val prepared = coordinator.recordRepairPrepared(assessed.id, plan)
        val applied = coordinator.recordRepairApplied(prepared.id, RepairResult(plan, RepairState.APPLIED, "backups/plan-rollback.bak", "ok"))
        val rolledBack = coordinator.recordRepairRolledBack(applied.id, RepairResult(plan, RepairState.ROLLED_BACK, "backups/plan-rollback.bak", "restored"))
        assertEquals(WorkflowPhase.NEEDS_REVIEW, rolledBack.phase)
    }

    @Test fun journalTamperingMakesCaseUnavailableForResume() {
        val root = Files.createTempDirectory("hubops-pro-tamper").toFile()
        val coordinator = ProfessionalWorkflowCoordinator(root)
        val started = coordinator.begin("build-003", "source.zip", hash, "projects/demo")
        val journal = File(root, "professional-workflows/events.chain")
        journal.appendText("\n${started.id}|2|dGFtcGVy|ZGV0YWls|${started.journalHeadSha256}|${"f".repeat(64)}")
        assertNull(coordinator.latest())
    }

    @Test(expected = IllegalArgumentException::class)
    fun reAuditCannotReuseTheSameBinaryArtifact() {
        val root = Files.createTempDirectory("hubops-pro-same-apk").toFile()
        val coordinator = ProfessionalWorkflowCoordinator(root)
        val started = coordinator.begin("build-005", "source.zip", hash, "projects/demo")
        val assessed = coordinator.recordAssessment(started.id, hash, "verification-1", VerificationStatus.PASSED, false, "e".repeat(64))
        val plan = RepairPlan("plan-same-apk", "projects/demo/Main.kt", hash, "b".repeat(64), hash, "approval")
        val prepared = coordinator.recordRepairPrepared(assessed.id, plan)
        val applied = coordinator.recordRepairApplied(prepared.id, RepairResult(plan, RepairState.APPLIED, "backups/plan-same-apk.bak", "ok"))
        coordinator.recordReaudit(applied.id, "verification-2", VerificationStatus.PASSED, "c".repeat(64), false,
            "source-v2.zip", "d".repeat(64), "build-005-r2", "e".repeat(64))
    }

    @Test fun snapshotTamperingMakesCaseUnavailableForResume() {
        val root = Files.createTempDirectory("hubops-pro-snapshot-tamper").toFile()
        val coordinator = ProfessionalWorkflowCoordinator(root)
        val started = coordinator.begin("build-004", "source.zip", hash, "projects/demo")
        val snapshot = File(root, "professional-workflows/${started.id}.properties")
        val props = java.util.Properties().apply { snapshot.inputStream().use { load(it) } }
        props.setProperty("phase", WorkflowPhase.REPAIR_APPLIED.name)
        snapshot.outputStream().use { props.store(it, "tampered") }
        assertNull(coordinator.latest())
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidBuildReferenceRejected() {
        val root = Files.createTempDirectory("hubops-pro-invalid").toFile()
        ProfessionalWorkflowCoordinator(root).begin("../escape", "source.zip", hash, "projects/demo")
    }
    @Test fun pendingCommitRecoversBeforeAndAfterJournalAppendWithoutDuplicateEvents() {
        listOf(false, true).forEach { journalAlreadyAppended ->
            val root = Files.createTempDirectory("hubops-pro-pending").toFile()
            val coordinator = ProfessionalWorkflowCoordinator(root)
            val started = coordinator.begin("build-pending", "source.zip", hash, "projects/demo")
            val snapshot = started.copy(lastMessage = "commit recuperado", eventCount = started.eventCount + 1)
            fun privateCall(name: String, vararg args: Any): Any {
                val method = coordinator.javaClass.declaredMethods.first { it.name == name && it.parameterCount == args.size }
                method.isAccessible = true
                return method.invoke(coordinator, *args)
            }
            val snapshotHash = privateCall("snapshotHash", snapshot) as String
            val event = "CRASH_RECOVERY_TEST"
            val detail = "pending-commit"
            val payload = listOf(started.id, snapshot.eventCount, event, detail, started.journalHeadSha256, snapshotHash).joinToString("|")
            val head = privateCall("sha256", payload) as String
            val line = listOf(started.id, snapshot.eventCount.toString(),
                Base64.getEncoder().encodeToString(event.toByteArray()),
                Base64.getEncoder().encodeToString(detail.toByteArray()), started.journalHeadSha256, snapshotHash, head).joinToString("|")
            val updated = snapshot.copy(journalHeadSha256 = head)
            val properties = privateCall("caseProperties", updated) as Properties
            properties.setProperty("pendingLine", line)
            val pendingPayload = properties.stringPropertyNames().sorted().joinToString("\n") { "$it=${properties.getProperty(it)}" }
            properties.setProperty("pendingHash", privateCall("sha256", pendingPayload) as String)
            val directory = File(root, "professional-workflows")
            File(directory, "${started.id}.pending").outputStream().use { properties.store(it, "crash simulation") }
            if (journalAlreadyAppended) File(directory, "events.chain").appendText(line + "\n")

            val recovered = ProfessionalWorkflowCoordinator(root).latest()
            assertNotNull(recovered)
            assertEquals(2, recovered!!.eventCount)
            assertEquals("commit recuperado", recovered.lastMessage)
            assertEquals(2, File(directory, "events.chain").readLines().count { it.startsWith(started.id + "|") })
            assertFalse(File(directory, "${started.id}.pending").exists())
            assertEquals(recovered.journalHeadSha256, ProfessionalWorkflowCoordinator(root).latest()?.journalHeadSha256)
            root.deleteRecursively()
        }
    }

}
