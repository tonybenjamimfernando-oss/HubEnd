package dev.hubend.app.bridge

import android.content.Intent
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import dev.hubops.BuildConfig as HubOpsBuildConfig
import dev.hubops.HubOpsApplication
import dev.hubops.core.integration.HubOpsEngineRegistry
import dev.hubops.core.integration.HubOpsOrchestrator
import dev.hubops.data.device.DeviceInfoService
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * Ponte Capacitor "HubEndEngines": expoe a interface HubEnd os motores HubOps reais.
 *
 * Regras:
 *  - Todo o trabalho corre num executor dedicado (nunca na thread da interface).
 *  - O ledger de integracao e apenas LIDO aqui; mutacoes entre motores continuam a passar
 *    pelo HubOpsOrchestrator com proveniencia, impacto e validacao (consola nativa).
 *  - O terminal local (ShellRunner) e os trabalhos Termux NAO sao expostos a WebView:
 *    a interface pode carregar conteudo remoto (APP_URL) e nao deve obter execucao de comandos.
 *    O terminal continua disponivel na consola nativa HubOps.
 *  - Erros sao devolvidos como rejeicoes explicitas, nunca como sucesso vazio.
 *  - Ciclo de vida (AR-01): todas as respostas passam pela BridgeLifecycleGate. Depois de
 *    handleOnDestroy nenhum resultado e entregue, as chamadas pendentes sao rejeitadas com
 *    UNAVAILABLE e o fecho e idempotente.
 */
@CapacitorPlugin(name = "HubEndEngines")
class HubEndEnginesPlugin : Plugin() {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "hubend-bridge").apply { isDaemon = true }
    }
    private val lifecycle = BridgeLifecycleGate<PluginCall>()
    private val stateDir: File get() = File(context.filesDir, "hubops-integration")

    @PluginMethod
    fun status(call: PluginCall) = respond(call) {
        val app = context.applicationContext as? HubOpsApplication
        val files = context.filesDir
        val device = DeviceInfoService(context).read()
        val shards = File(files, "models/shards").listFiles { f -> f.extension == "gguf" }?.size ?: 0
        val omega = BridgePayloads.OmegaFacts(
            releaseKeyConfigured = app?.omegaEngines?.releasePublicKeyOrNull() != null,
            llamaRuntimeInstalled = File(files, "native/llama-cli").isFile,
            snseShardFiles = shards,
            prootInstalled = File(files, "native/proot").isFile,
            alpineRootfsInstalled = File(files, "oci/rootfs/alpine_3.24.4").isDirectory,
            termuxInstalled = device.termuxInstalled,
        )
        JSONObject()
            .put("contractVersion", BridgePayloads.CONTRACT_VERSION)
            .put("platform", "android")
            .put("hubopsVersion", HubOpsBuildConfig.HUBOPS_VERSION)
            .put("appVersion", context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "")
            .put("engines", org.json.JSONArray(HubOpsEngineRegistry.all().map(BridgePayloads::engine)))
            .put("omega", BridgePayloads.omega(omega))
            .put("device", BridgePayloads.device(device))
    }

    @PluginMethod
    fun ledger(call: PluginCall) = respond(call) {
        val limit = BridgePayloads.clampLimit(call.getInt("limit"))
        val orchestrator = HubOpsOrchestrator(stateDir)
        JSONObject()
            .put("auditLines", org.json.JSONArray(orchestrator.recentAuditLines(limit)))
            .put("projectRecords", org.json.JSONArray(orchestrator.recentProjectRecords(limit).map(BridgePayloads::record)))
            .put("pendingProposals", org.json.JSONArray(orchestrator.pendingProposals().map(BridgePayloads::proposal)))
            .put("recentTransactions", orchestrator.recentTransactions(limit).size)
    }

    @PluginMethod
    fun openNativeConsole(call: PluginCall) {
        if (!lifecycle.track(call)) {
            rejectUnavailable(call)
            return
        }
        val host = activity
        if (host == null) {
            lifecycle.complete(call)
            call.reject("Atividade indisponivel.")
            return
        }
        host.runOnUiThread {
            lifecycle.deliver(call) { target ->
                host.startActivity(Intent(host, dev.hubops.ui.MainActivity::class.java))
                target.resolve(JSObject().put("opened", true))
            }
        }
    }

    override fun handleOnDestroy() {
        if (lifecycle.state() != BridgeLifecycleGate.State.DESTROYED) {
            // Rejeita o que ficou pendente antes de desligar o executor: nenhuma promessa JavaScript
            // fica suspensa e nenhum callback tardio chega a uma WebView ja destruida.
            lifecycle.beginClosing().forEach { pending ->
                runCatching { pending.reject("Atividade encerrada.", "UNAVAILABLE") }
            }
            executor.shutdownNow()
            lifecycle.seal()
        }
        super.handleOnDestroy()
    }

    private fun respond(call: PluginCall, work: () -> JSONObject) {
        if (!lifecycle.track(call)) {
            rejectUnavailable(call)
            return
        }
        val accepted = try {
            executor.execute {
                try {
                    val payload = work()
                    lifecycle.deliver(call) { target -> target.resolve(JSObject.fromJSONObject(payload)) }
                } catch (invalid: IllegalArgumentException) {
                    lifecycle.deliver(call) { target ->
                        target.reject(invalid.message ?: "Pedido invalido.", "INVALID_ARGUMENT")
                    }
                } catch (error: Exception) {
                    lifecycle.deliver(call) { target ->
                        target.reject(error.message ?: error.javaClass.simpleName, "ENGINE_ERROR", error)
                    }
                }
            }
            true
        } catch (rejected: RejectedExecutionException) {
            false
        }
        if (!accepted) {
            lifecycle.complete(call)
            rejectUnavailable(call)
        }
    }

    private fun rejectUnavailable(call: PluginCall) {
        runCatching { call.reject("Ponte encerrada.", "UNAVAILABLE") }
    }
}