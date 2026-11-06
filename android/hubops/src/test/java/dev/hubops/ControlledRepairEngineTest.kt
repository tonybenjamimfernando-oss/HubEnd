package dev.hubops

import dev.hubops.core.repair.ControlledRepairEngine
import dev.hubops.core.repair.RepairState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ControlledRepairEngineTest {
    @Rule @JvmField val temp = TemporaryFolder()

    @Test fun appliesOnlyAfterApprovalAndCanRestoreVerifiedBackup() {
        val workspace = temp.newFolder("workspace")
        val state = temp.newFolder("state")
        val target = java.io.File(workspace, "config/settings.txt").apply { parentFile.mkdirs(); writeText("old") }
        val replacement = temp.newFile("replacement.txt").apply { writeText("new") }
        val engine = ControlledRepairEngine(workspace, state)
        val plan = engine.prepare("config/settings.txt", replacement)
        assertEquals("old", target.readText())
        assertThrows(IllegalArgumentException::class.java) { engine.approveAndApply(plan, "wrong", replacement) }
        val applied = engine.approveAndApply(plan, plan.approvalToken, replacement)
        assertEquals(RepairState.APPLIED, applied.state)
        assertEquals("new", target.readText())
        val rolledBack = engine.rollback(plan.id)
        assertEquals(RepairState.ROLLED_BACK, rolledBack.state)
        assertEquals("old", target.readText())
    }

    @Test fun refusesIfDestinationChangedAfterPreparation() {
        val workspace = temp.newFolder("workspace")
        val state = temp.newFolder("state")
        val target = java.io.File(workspace, "a.txt").apply { writeText("old") }
        val replacement = temp.newFile("replacement.txt").apply { writeText("new") }
        val engine = ControlledRepairEngine(workspace, state)
        val plan = engine.prepare("a.txt", replacement)
        target.writeText("external-change")
        assertThrows(IllegalArgumentException::class.java) { engine.approveAndApply(plan, plan.approvalToken, replacement) }
        assertEquals("external-change", target.readText())
    }

    @Test fun refusesTraversalAndDoesNotAutoDeleteNewFilesOnRollback() {
        val workspace = temp.newFolder("workspace")
        val state = temp.newFolder("state")
        val replacement = temp.newFile("replacement.txt").apply { writeText("new") }
        val engine = ControlledRepairEngine(workspace, state)
        assertThrows(IllegalArgumentException::class.java) { engine.prepare("../outside.txt", replacement) }
        val plan = engine.prepare("new.txt", replacement)
        engine.approveAndApply(plan, plan.approvalToken, replacement)
        assertThrows(IllegalArgumentException::class.java) { engine.rollback(plan.id) }
        assertEquals("new", java.io.File(workspace, "new.txt").readText())
    }
}
