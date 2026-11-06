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

/**
 * Ponte Capacitor "HubEndEngines": expõe à interface HubEnd os motores HubOps reais.
 *
 * Regras:
 *  - Todo o trabalho corre num executor dedicado (nunca na thread da interface).
 *  - O ledger de integração é apenas LIDO aqui; mutações entre motores continuam a passar
 *    pelo HubOpsOrchestrator com proveniência, impacto e validação (consola nativa).
 *  - O terminal local (ShellRunner) e os trabalhos Termux NÃO são expostos à WebView:
 *    a interface pode carregar conteúdo remoto (APP_URL) e não deve obter execução de comandos.
 *    O terminal continua disponível na consola nativa HubOps.
 *  - Erros são devolvidos como rejeições explícitas, nunca como sucesso vazio.
 */
@CapacitorPlugin(name = "HubEndEngines")
class HubEndEnginesPlugin : Plugin() {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "hubend-bridge").apply { isDaemon = true }
    }
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
        val host = activity
        if (host == null) {
            call.reject("Atividade indisponível.")
            return
        }
        host.runOnUiThread {
            host.startActivity(Intent(host, dev.hubops.ui.MainActivity::class.java))
            call.resolve(JSObject().put("opened", true))
        }
    }

    override fun handleOnDestroy() {
        executor.shutdownNow()
        super.handleOnDestroy()
    }

    private fun respond(call: PluginCall, work: () -> JSONObject) {
        executor.execute {
            try {
                call.resolve(JSObject.fromJSONObject(work()))
            } catch (e: IllegalArgumentException) {
                call.reject(e.message ?: "Pedido inválido.", "INVALID_ARGUMENT")
            } catch (e: Exception) {
                call.reject(e.message ?: e.javaClass.simpleName, "ENGINE_ERROR", e)
            }
        }
    }
}
