package dev.hubend.app.bridge

import dev.hubops.core.integration.EngineId
import dev.hubops.core.integration.HubOpsEngineRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgePayloadsTest {

    @Test
    fun ledgerLimitIsClampedBetweenOneAndOneHundred() {
        assertEquals(30, BridgePayloads.clampLimit(null))
        assertEquals(1, BridgePayloads.clampLimit(0))
        assertEquals(1, BridgePayloads.clampLimit(-5))
        assertEquals(100, BridgePayloads.clampLimit(5000))
        assertEquals(42, BridgePayloads.clampLimit(42))
    }

    @Test
    fun engineDescriptorKeepsRegistryFlowPolicies() {
        val hub = HubOpsEngineRegistry.get(EngineId.HUBOPS)!!
        val json = BridgePayloads.engine(hub)
        assertEquals("HUBOPS", json.getString("id"))
        val outbound = json.getJSONArray("outbound")
        val modes = (0 until outbound.length()).associate {
            outbound.getJSONObject(it).getString("target") to outbound.getJSONObject(it).getString("mode")
        }
        assertEquals("GOVERNED_MUTATION", modes["DESPERTAR"])
        assertEquals("EVIDENCE_ONLY", modes["VISLUMBOPS"])
    }

    @Test
    fun allFourRegistryEnginesAreExposed() {
        val ids = HubOpsEngineRegistry.all().map { BridgePayloads.engine(it).getString("id") }.toSet()
        assertEquals(setOf("DESPERTAR", "ESPELHAR_X", "VISLUMBOPS", "HUBOPS"), ids)
    }

    @Test
    fun neuralSnseIsOnlyOperationalWithRuntimeAndShards() {
        val base = BridgePayloads.OmegaFacts(false, false, 0, false, false, false)
        assertFalse(BridgePayloads.omega(base).getBoolean("snseNeuralOperational"))
        assertFalse(BridgePayloads.omega(base.copy(llamaRuntimeInstalled = true)).getBoolean("snseNeuralOperational"))
        assertFalse(BridgePayloads.omega(base.copy(snseShardFiles = 3)).getBoolean("snseNeuralOperational"))
        assertTrue(BridgePayloads.omega(base.copy(llamaRuntimeInstalled = true, snseShardFiles = 1)).getBoolean("snseNeuralOperational"))
    }

    @Test
    fun nscIsolationNeedsProotAndRootfs() {
        val base = BridgePayloads.OmegaFacts(false, false, 0, true, false, true)
        assertFalse(BridgePayloads.omega(base).getBoolean("nscIsolationAvailable"))
        assertTrue(BridgePayloads.omega(base.copy(alpineRootfsInstalled = true)).getBoolean("nscIsolationAvailable"))
    }
}
