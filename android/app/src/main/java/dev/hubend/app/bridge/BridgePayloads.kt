package dev.hubend.app.bridge

import dev.hubops.core.integration.EngineDescriptor
import dev.hubops.core.integration.ProjectRecord
import dev.hubops.core.integration.TransferProposal
import dev.hubops.core.model.DeviceInfo
import org.json.JSONArray
import org.json.JSONObject

/**
 * Contrato de dados da ponte HubEnd ↔ HubOps.
 *
 * Funções puras (sem Android) que convertem objetos dos motores HubOps em JSON estável
 * consumido pela interface (src/lib/bridge/contract.ts). Qualquer campo novo deve ser
 * acrescentado nos dois lados; os testes em app/src/test fixam o formato.
 */
object BridgePayloads {
    const val CONTRACT_VERSION = 1
    const val MAX_LEDGER_LIMIT = 100

    fun clampLimit(requested: Int?): Int = (requested ?: 30).coerceIn(1, MAX_LEDGER_LIMIT)

    fun engine(descriptor: EngineDescriptor): JSONObject = JSONObject()
        .put("id", descriptor.id.name)
        .put("displayName", descriptor.displayName)
        .put("capabilities", JSONArray(descriptor.capabilities.sorted()))
        .put("outbound", JSONArray().apply {
            descriptor.outbound.entries.sortedBy { it.key.name }.forEach { (target, mode) ->
                put(JSONObject().put("target", target.name).put("mode", mode.name))
            }
        })

    fun device(info: DeviceInfo): JSONObject = JSONObject()
        .put("androidRelease", info.androidRelease)
        .put("sdk", info.sdk)
        .put("model", info.model)
        .put("batteryPercent", info.batteryPercent)
        .put("batteryCharging", info.batteryCharging)
        .put("availableRamBytes", info.availableRamBytes)
        .put("totalRamBytes", info.totalRamBytes)
        .put("freeBytes", info.freeBytes)
        .put("totalBytes", info.totalBytes)
        .put("uptimeMillis", info.uptimeMillis)
        .put("termuxInstalled", info.termuxInstalled)
        .put("shizukuInstalled", info.shizukuInstalled)

    fun proposal(p: TransferProposal): JSONObject = JSONObject()
        .put("id", p.id)
        .put("sourceEngine", p.provenance.sourceEngine.name)
        .put("destinationEngine", p.destinationEngine.name)
        .put("artifactName", p.provenance.artifactName)
        .put("artifactSha256", p.provenance.artifactSha256)
        .put("status", p.status.name)
        .put("destinationPrefix", p.impact.destinationPrefix)
        .put("supportedFiles", p.impact.supportedFiles)
        .put("collisions", p.impact.collisions.size)
        .put("riskNotes", JSONArray(p.impact.riskNotes))
        .put("createdAtEpochMs", p.createdAtEpochMs)

    fun record(r: ProjectRecord): JSONObject = JSONObject()
        .put("proposalId", r.proposalId)
        .put("sourceEngine", r.sourceEngine.name)
        .put("destinationEngine", r.destinationEngine.name)
        .put("artifactName", r.artifactName)
        .put("artifactSha256", r.artifactSha256)
        .put("acceptedFiles", r.acceptedFiles)
        .put("acceptedAtEpochMs", r.acceptedAtEpochMs)

    /** Estado verificável da suite Omega: só reporta o que existe no dispositivo. */
    data class OmegaFacts(
        val releaseKeyConfigured: Boolean,
        val llamaRuntimeInstalled: Boolean,
        val snseShardFiles: Int,
        val prootInstalled: Boolean,
        val alpineRootfsInstalled: Boolean,
        val termuxInstalled: Boolean,
    )

    fun omega(f: OmegaFacts): JSONObject = JSONObject()
        .put("releaseKeyConfigured", f.releaseKeyConfigured)
        .put("llamaRuntimeInstalled", f.llamaRuntimeInstalled)
        .put("snseShardFiles", f.snseShardFiles)
        .put("prootInstalled", f.prootInstalled)
        .put("alpineRootfsInstalled", f.alpineRootfsInstalled)
        .put("termuxInstalled", f.termuxInstalled)
        // Um especialista neural só é operacional com runtime + shards reais instalados.
        .put("snseNeuralOperational", f.llamaRuntimeInstalled && f.snseShardFiles > 0)
        .put("nscIsolationAvailable", f.prootInstalled && f.alpineRootfsInstalled)
}
