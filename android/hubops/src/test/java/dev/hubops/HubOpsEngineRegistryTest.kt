package dev.hubops

import dev.hubops.core.integration.*
import org.junit.Assert.*
import org.junit.Test

class HubOpsEngineRegistryTest {
    @Test fun registersAllFourEnginesAndExplicitFlows() {
        assertEquals(4, HubOpsEngineRegistry.all().size)
        assertEquals(EngineFlowMode.GOVERNED_MUTATION, HubOpsEngineRegistry.flow(EngineId.DESPERTAR, EngineId.ESPELHAR_X))
        assertEquals(EngineFlowMode.READ_ONLY_ANALYSIS, HubOpsEngineRegistry.flow(EngineId.ESPELHAR_X, EngineId.DESPERTAR))
        assertEquals(EngineFlowMode.EVIDENCE_ONLY, HubOpsEngineRegistry.flow(EngineId.VISLUMBOPS, EngineId.HUBOPS))
        assertNull(HubOpsEngineRegistry.flow(EngineId.VISLUMBOPS, EngineId.DESPERTAR))
    }
}
