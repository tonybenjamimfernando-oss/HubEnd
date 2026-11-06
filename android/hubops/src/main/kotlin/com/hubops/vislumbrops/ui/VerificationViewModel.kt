package com.hubops.vislumbrops.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hubops.vislumbrops.core.*
import com.hubops.vislumbrops.reporting.VislumbropsReport
import dev.hubops.core.integration.HubOpsVerificationRelay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class UiState(
    val running: Boolean = false,
    val phase: String = "",
    val log: List<String> = emptyList(),
    val report: VislumbropsReport? = null,
    val error: String? = null
)

class VerificationViewModel : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state
    private var job: Job? = null
    private var lastArtifactPath: String? = null

    fun start(projectId: String, artifactPath: String) {
        lastArtifactPath = artifactPath
        job?.cancel()
        _state.value = UiState(running = true, phase = "PREPARING")
        runVerification(projectId, artifactPath)
    }

    fun startFromUri(context: Context, projectId: String, uri: Uri) {
        job?.cancel()
        _state.value = UiState(running = true, phase = "IMPORTING_APK", log = listOf("A copiar APK para armazenamento privado..."))
        val target = File(context.cacheDir, "vislumbrops-${UUID.randomUUID()}.apk")
        job = viewModelScope.launch {
            try {
                val cached = withContext(Dispatchers.IO) {
                    val input = requireNotNull(context.contentResolver.openInputStream(uri)) {
                        "Não foi possível abrir o ficheiro selecionado."
                    }
                    input.use { source ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(32 * 1024)
                            var total = 0L
                            while (true) {
                                val count = source.read(buffer)
                                if (count < 0) break
                                total += count
                                require(total <= 100L * 1024 * 1024) { "APK excede o limite de 100 MB." }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    require(target.length() > 0L) { "O ficheiro selecionado está vazio." }
                    target
                }
                lastArtifactPath = cached.absolutePath
                _state.value = _state.value.copy(phase = "ANALYZING", log = _state.value.log + "APK importado: ${cached.name}")
                runVerification(projectId, cached.absolutePath)
            } catch (cancelled: CancellationException) {
                target.delete()
                throw cancelled
            } catch (error: Exception) {
                target.delete()
                _state.value = _state.value.copy(
                    running = false,
                    phase = "IMPORT_FAILED",
                    error = error.message ?: "Falha ao importar APK"
                )
                log("Falha ao importar APK: ${error.message}")
            }
        }
    }

    private fun runVerification(projectId: String, artifactPath: String) {
        job = viewModelScope.launch {
            val contract = VerificationContract(projectId = projectId, artifactPath = artifactPath)
            ServiceLocator.orchestrator.verify(contract).collect { ev ->
                when (ev) {
                    is VerificationEvent.Started -> log("Início: ${ev.contract.verificationId}")
                    is VerificationEvent.Phase -> {
                        _state.value = _state.value.copy(phase = ev.name)
                        log("Fase: ${ev.name}")
                    }
                    is VerificationEvent.PartialUpdate -> log(
                        "checks=${ev.contract.checks.size}, findings=${ev.contract.findings.size}"
                    )
                    is VerificationEvent.Warning -> log("⚠ ${ev.message}")
                    is VerificationEvent.Completed -> {
                        _state.value = _state.value.copy(running = false, report = ev.report, phase = "COMPLETED")
                        log("Concluído: score=${ev.contract.score?.overall}")
                        val relayResult = runCatching {
                            HubOpsVerificationRelay(ServiceLocator.hubOpsBridge, ServiceLocator.hubLedger)
                                .publish(ev.contract, ev.report)
                        }.getOrElse { error ->
                            log("Integração HubOps não publicada: ${error.message ?: "falha no relay"}")
                            "NOT_PUBLISHED"
                        }
                        log("HubOps ledger: $relayResult (sem alterações ao projeto).")
                    }
                    is VerificationEvent.Failed -> {
                        _state.value = _state.value.copy(running = false, error = ev.cause.message)
                        log("✗ Falha: ${ev.cause.message}")
                    }
                    is VerificationEvent.Cancelled -> {
                        _state.value = _state.value.copy(running = false)
                        log("Cancelado")
                    }
                }
            }
        }
    }


    fun exportSupplyChainManifest(context: Context, destination: Uri) {
        val path = lastArtifactPath
        if (path == null) {
            _state.value = _state.value.copy(error = "Não existe APK verificado para exportar.")
            return
        }
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    val audit = ServiceLocator.supplyChainEngine.audit(path)
                    val manifest = requireNotNull(audit.manifest) {
                        "Não foi possível gerar o manifesto: ${audit.result.checks.firstOrNull()?.description ?: "auditoria indisponível"}"
                    }
                    manifest.toJson()
                }
                withContext(Dispatchers.IO) {
                    val output = requireNotNull(context.contentResolver.openOutputStream(destination)) {
                        "Não foi possível abrir o destino para escrita."
                    }
                    output.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                }
                log("Manifesto de cadeia de fornecimento exportado.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.value = _state.value.copy(error = error.message ?: "Falha ao exportar manifesto")
            }
        }
    }

    fun cancel() { job?.cancel() }

    private fun log(msg: String) {
        _state.value = _state.value.copy(log = _state.value.log + msg)
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }
}
