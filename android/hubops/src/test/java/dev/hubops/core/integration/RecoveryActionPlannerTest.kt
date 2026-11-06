package dev.hubops.core.integration

import dev.hubops.core.recovery.RecoveryState
import org.junit.Assert.*
import org.junit.Test

class RecoveryActionPlannerTest {
    private fun result(status: ReconciliationStatus) = RecoveryReconciliation(
        transactionId = "tx-1", proposalId = "proposal-1", transactionState = RecoveryState.NEEDS_ATTENTION,
        status = status, artifactAvailable = true, stagingAvailable = status == ReconciliationStatus.STAGING_AVAILABLE_FOR_REVIEW,
        destinationAvailable = false, ledgerRecordAvailable = false, explanation = "fixture"
    )

    @Test fun everyPlanIsAdvisoryAndNeverAutomaticallyExecutable() {
        ReconciliationStatus.values().forEach { status ->
            val plan = RecoveryActionPlanner.plan(result(status))
            assertFalse(plan.automaticExecutionAllowed)
            assertEquals(64, plan.planSha256.length)
            assertTrue(plan.actions.isNotEmpty())
        }
    }

    @Test fun stagingRequiresInspectionAndFreshValidation() {
        val plan = RecoveryActionPlanner.plan(result(ReconciliationStatus.STAGING_AVAILABLE_FOR_REVIEW))
        assertTrue(plan.actions.any { it.kind == RecoveryActionKind.INSPECT_STAGING && it.requiresHumanApproval })
        assertTrue(plan.actions.any { it.kind == RecoveryActionKind.REVALIDATE_STAGING && it.requiresFreshValidation })
        assertTrue(plan.actions.any { it.kind == RecoveryActionKind.BLOCK_AUTOMATIC_RECOVERY })
    }

    @Test fun committedConsistentOperationIsNoOp() {
        val plan = RecoveryActionPlanner.plan(result(ReconciliationStatus.CONSISTENT_COMMITTED))
        assertEquals(listOf(RecoveryActionKind.NO_ACTION), plan.actions.map { it.kind })
    }

    @Test fun planHashIsDeterministicForSameEvidence() {
        val evidence = result(ReconciliationStatus.NEEDS_MANUAL_REVIEW)
        assertEquals(RecoveryActionPlanner.plan(evidence).planSha256, RecoveryActionPlanner.plan(evidence).planSha256)
    }
}
