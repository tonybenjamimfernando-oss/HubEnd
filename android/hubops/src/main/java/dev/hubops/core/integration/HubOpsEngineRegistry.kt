package dev.hubops.core.integration

/** Exchange policy between autonomous engines. Policies are explicit, not inferred from UI routes. */
enum class EngineFlowMode { GOVERNED_MUTATION, READ_ONLY_ANALYSIS, EVIDENCE_ONLY }

data class EngineDescriptor(
    val id: EngineId,
    val displayName: String,
    val capabilities: Set<String>,
    val outbound: Map<EngineId, EngineFlowMode>,
)

object HubOpsEngineRegistry {
    private val descriptors = listOf(
        EngineDescriptor(
            EngineId.HUBOPS, "HubOps Core",
            setOf("engine-registry", "event-coordination", "audit-ledger", "provenance", "orchestration",
                "snse-neuro-symbolic-analysis", "nsc-tiered-runtime-planning", "gopa-signed-audit-attestations"),
            mapOf(
                EngineId.DESPERTAR to EngineFlowMode.GOVERNED_MUTATION,
                EngineId.ESPELHAR_X to EngineFlowMode.GOVERNED_MUTATION,
                EngineId.VISLUMBOPS to EngineFlowMode.EVIDENCE_ONLY,
            ),
        ),
        EngineDescriptor(
            EngineId.DESPERTAR, "Despertar",
            setOf("project-generation", "project-repair", "build-preparation"),
            mapOf(EngineId.ESPELHAR_X to EngineFlowMode.GOVERNED_MUTATION),
        ),
        EngineDescriptor(
            EngineId.ESPELHAR_X, "Espelhar-X",
            setOf("project-inspection", "ast-analysis", "workspace", "diff", "repair-proposals"),
            mapOf(EngineId.DESPERTAR to EngineFlowMode.READ_ONLY_ANALYSIS),
        ),
        EngineDescriptor(
            EngineId.VISLUMBOPS, "VislumbOps",
            setOf("apk-integrity", "supply-chain", "permissions", "secrets", "dependencies", "evidence", "attestation", "reports"),
            mapOf(EngineId.HUBOPS to EngineFlowMode.EVIDENCE_ONLY),
        ),
    ).associateBy { it.id }

    fun all(): List<EngineDescriptor> = descriptors.values.sortedBy { it.id.name }
    fun get(id: EngineId): EngineDescriptor? = descriptors[id]
    fun flow(source: EngineId, destination: EngineId): EngineFlowMode? =
        descriptors[source]?.outbound?.get(destination)

    fun requireFlow(source: EngineId, destination: EngineId, expected: EngineFlowMode) {
        require(flow(source, destination) == expected) {
            "Fluxo não autorizado pela rede HubOps: $source -> $destination (esperado: $expected)."
        }
    }
}
