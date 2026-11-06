package dev.hubops.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hubops.vislumbrops.core.CheckCategory
import com.hubops.vislumbrops.core.Finding
import com.hubops.vislumbrops.core.Severity
import dev.hubops.HubOpsApplication
import dev.hubops.ui.HubViewModel
import dev.hubops.omega.containers.WorkloadProfile
import kotlinx.coroutines.launch

/** Operational console for SNSE/NSC; GoPA remains disabled until a trusted peer set is provisioned. */
@Composable
fun OmegaScreen(vm: HubViewModel) {
    val context = LocalContext.current
    val suite = (context.applicationContext as HubOpsApplication).omegaEngines
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("db.rawQuery(\"SELECT * FROM users WHERE id=\" + userId, null)") }
    var cwe by remember { mutableStateOf("CWE-89") }
    var targetPath by remember { mutableStateOf("") }
    val notice by vm.notice.collectAsState()
    var snseStatus by remember { mutableStateOf("Aguardando análise") }
    var snseDetail by remember { mutableStateOf("") }
    var patchCandidate by remember { mutableStateOf<String?>(null) }
    var sandboxDetail by remember { mutableStateOf("Ainda não avaliado") }
    var modelShard by remember { mutableStateOf("sqli") }
    var modelStatus by remember { mutableStateOf("Modelos são importados individualmente e verificados antes da instalação.") }
    var busy by remember { mutableStateOf(false) }
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val key = suite.releasePublicKeyOrNull()
                if (key == null) modelStatus = "Chave pública de release ausente nesta build."
                else {
                    modelStatus = "A verificar assinatura, hash e tamanho do GGUF…"
                    val result = suite.installSnseShard(key, modelShard.trim().lowercase(), uri)
                    result.fold(
                        onSuccess = { modelStatus = "${it.name}: instalado e verificado (${it.sizeBytes / (1024 * 1024)} MB)." },
                        onFailure = { modelStatus = "Importação recusada: ${it.message}" }
                    )
                }
            } finally { busy = false }
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("OMEGA · MOTORES AVANÇADOS", style = MaterialTheme.typography.headlineSmall)
            Text("SNSE · NSC · GoPA", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
            Text("Motores especializados integrados ao ciclo HubOps. Nenhuma proposta de IA é aplicada automaticamente.", style = MaterialTheme.typography.bodySmall)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("01 · SNSE — análise neuro-simbólica", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(cwe, { cwe = it; patchCandidate = null; snseDetail = "" }, label = { Text("CWE (ex.: CWE-89)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(code, { code = it; patchCandidate = null; snseDetail = "" }, label = { Text("Código-fonte exato do ficheiro") }, modifier = Modifier.fillMaxWidth(), minLines = 5)
                    OutlinedTextField(targetPath, { targetPath = it }, label = { Text("Destino no workspace Espelhar-X (ex.: projects/app/src/Main.kt)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(enabled = !busy && code.isNotBlank() && cwe.matches(Regex("CWE-[0-9]{1,5}")), onClick = {
                        busy = true
                        snseStatus = "A analisar"
                        patchCandidate = null
                        scope.launch {
                            try {
                                val finding = Finding(category = CheckCategory.STATIC_ANALYSIS, severity = Severity.HIGH,
                                    title = "Análise solicitada pelo utilizador", description = "Analisar código com regras SNSE.", cweId = cwe)
                                val result = suite.snse.generate(finding, code)
                                snseStatus = result.mode + if (result.acceptedForReview) " · proposta para revisão" else " · sem patch aprovado"
                                patchCandidate = result.finalPatch.takeIf { result.acceptedForReview }
                                snseDetail = buildString {
                                    appendLine(result.arbitrationReason)
                                    appendLine("Rotas: ${result.routes.joinToString { "${it.shardId} (${(it.confidence * 100).toInt()}%)" }.ifBlank { "nenhuma" }}")
                                    appendLine("Execuções: ${result.executions.size}; propostas: ${result.executions.count { it.output != null }}")
                                    result.limitations.forEach { appendLine("• $it") }
                                    result.finalPatch?.let { appendLine("\nPATCH PROPOSTO (não aplicado):\n$it") }
                                }
                            } catch (e: Exception) {
                                snseStatus = "Falha controlada"
                                patchCandidate = null
                                snseDetail = e.message ?: "Erro desconhecido"
                            } finally { busy = false }
                        }
                    }) { Text(if (busy) "A analisar…" else "Executar SNSE") }
                    Text("Estado: $snseStatus", style = MaterialTheme.typography.titleSmall)
                    if (snseDetail.isNotBlank()) Text(snseDetail, style = MaterialTheme.typography.bodySmall)
                    if (patchCandidate != null && targetPath.isNotBlank() && snseStatus.startsWith("HYBRID_PROPOSAL")) {
                        OutlinedButton(enabled = !busy, onClick = {
                            vm.prepareProfessionalRepairFromPatch(targetPath.trim(), code, patchCandidate!!)
                        }) { Text("Enviar proposta ao Pro Flow") }
                    }
                    notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = !busy, onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    val key = suite.releasePublicKeyOrNull()
                                    if (key == null) modelStatus = "Chave pública de release ausente nesta build."
                                    else {
                                        modelStatus = "A validar e instalar llama.cpp…"
                                        val result = suite.installLlamaCliRuntime(key)
                                        modelStatus = if (result.isSuccess) "Runtime llama.cpp instalado e anexado ao SNSE." else "Runtime indisponível: ${result.exceptionOrNull()?.message}"
                                    }
                                } finally { busy = false }
                            }
                        }) { Text("Instalar runtime") }
                    }
                    OutlinedTextField(modelShard, { modelShard = it.lowercase() }, label = { Text("Shard: sqli, crypto, auth, network, storage, logging, deserialization") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedButton(enabled = !busy && modelShard.trim() in setOf("sqli", "crypto", "auth", "network", "storage", "logging", "deserialization"), onClick = {
                        modelPicker.launch(arrayOf("application/octet-stream", "application/x-gguf", "*/*"))
                    }) { Text("Importar modelo GGUF") }
                    Text(modelStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    Text("Os sete modelos são distribuídos separadamente do APK. O runtime e cada GGUF só são ativados após validação Ed25519 e SHA-256. A execução ainda depende de compatibilidade do Android/CPU e deve ser medida no dispositivo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("02 · NSC — cascata de isolamento", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = !busy, onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    val key = suite.releasePublicKeyOrNull()
                                    sandboxDetail = if (key == null) "Chave pública de release ausente nesta build." else {
                                        val report = suite.bootstrapAlpine(key)
                                        "PRoot=${report.prootReady}; Alpine=${report.rootfsReady}: ${report.message}"
                                    }
                                } finally { busy = false }
                            }
                        }) { Text("Instalar PRoot + Alpine") }
                        OutlinedButton(enabled = !busy, onClick = {
                            val plan = suite.sandbox.plan(WorkloadProfile(requiresUnixBinary = false))
                            sandboxDetail = "${plan.level}: ${plan.rationale}\n" + plan.warnings.joinToString("\n• ", prefix = "• ")
                        }) { Text("Avaliar JVM") }
                        OutlinedButton(enabled = !busy, onClick = {
                            val plan = suite.sandbox.plan(WorkloadProfile(requiresUnixBinary = true, expectedRamMb = 512))
                            sandboxDetail = "${plan.level} · pronto=${plan.ready}: ${plan.rationale}\n" + plan.warnings.joinToString("\n• ", prefix = "• ")
                        }) { Text("Avaliar UNIX") }
                    }
                    Text(sandboxDetail, style = MaterialTheme.typography.bodySmall)
                    Text("JVM = análise estática, não execução isolada. PRoot só fica disponível com binário executável e rootfs privados verificados; não equivale a Docker nem a isolamento de kernel. Termux exige delegação explicitamente aprovada.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("03 · GoPA — prova de auditoria", style = MaterialTheme.typography.titleMedium)
                    Text("Motor criptográfico disponível por API. A rede permanece desativada até existir um diretório de peers confiáveis, chaves públicas verificadas e transporte gossip configurado.", style = MaterialTheme.typography.bodySmall)
                    Text("Assinaturas Ed25519 · raiz Merkle · quórum por peers distintos (⌊2M/3⌋+1). Não constitui BFT geral, não impede Sybil/colusão e não valida o conteúdo das evidências sem os artefactos originais.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
