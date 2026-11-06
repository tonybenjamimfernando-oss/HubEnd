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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hubops.vislumbrops.ui.VerificationViewModel
import dev.hubops.core.integration.WorkflowPhase
import dev.hubops.ui.HubViewModel

/** Guided, auditable path. Mutations remain separately approved and require a rebuilt artifact. */
@Composable
fun ProfessionalWorkflowScreen(vm: HubViewModel) {
    val context = LocalContext.current
    val verificationVm: VerificationViewModel = viewModel()
    val audit by verificationVm.state.collectAsState()
    val case by vm.professionalCase.collectAsState()
    val assessment by vm.professionalAssessment.collectAsState()
    val insight by vm.projectInsight.collectAsState()
    val reAuditReady by vm.professionalReauditReady.collectAsState()
    val busy by vm.busy.collectAsState()
    var buildRef by rememberSaveable { mutableStateOf("build-${System.currentTimeMillis()}") }
    var workspacePrefix by rememberSaveable { mutableStateOf("projects/my-app") }
    var target by rememberSaveable { mutableStateOf("") }
    var confirmRepair by remember { mutableStateOf(false) }
    var confirmRollback by remember { mutableStateOf(false) }
    var apkName by rememberSaveable { mutableStateOf("Nenhum APK auditado nesta sessão") }
    var previousVerificationId by rememberSaveable { mutableStateOf<String?>(null) }
    var workflowAuditReady by rememberSaveable { mutableStateOf(false) }

    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            apkName = it.lastPathSegment ?: "APK selecionado"
            previousVerificationId = audit.report?.contract?.verificationId
            workflowAuditReady = false
            verificationVm.startFromUri(context, case?.id ?: "hubops-project", it)
        }
    }
    LaunchedEffect(audit.report?.contract?.verificationId) {
        val id = audit.report?.contract?.verificationId
        if (id != null && id != previousVerificationId) workflowAuditReady = true
    }

    val replacementPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.prepareProfessionalRepair(context, target.trim(), it) }
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("HUBOPS · PRO WORKFLOW", style = MaterialTheme.typography.headlineSmall)
            Text("Diagnóstico → evidências → plano → aprovação → reparação → nova auditoria",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("01 · Origem e diagnóstico", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(buildRef, { buildRef = it }, label = { Text("Referência única da build") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(workspacePrefix, { workspacePrefix = it }, label = { Text("Projeto dentro do workspace Espelhar-X") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { workflowAuditReady = false; vm.beginProfessionalWorkflow(workspacePrefix, buildRef.trim()) }, enabled = !busy && workspacePrefix.isNotBlank() && buildRef.isNotBlank()) {
                            Text("Criar caso e analisar workspace")
                        }
                        if (case != null && case?.phase !in setOf<WorkflowPhase?>(WorkflowPhase.REPAIR_APPLIED, WorkflowPhase.REAUDIT_COMPLETE)) {
                            OutlinedButton(onClick = { workflowAuditReady = false; vm.resumeProfessionalWorkflow(workspacePrefix) }, enabled = !busy) { Text("Retomar caso") }
                        }
                    }
                    Text("O snapshot é exportado diretamente do workspace privado. A reparação fica limitada ao mesmo prefixo do projeto; não é permitido corrigir um projeto diferente do analisado.", style = MaterialTheme.typography.bodySmall)
                    insight?.let { Text("Diagnóstico: ${it.filesScanned} ficheiros · AST ${it.astReport.filesParsed}/${it.astReport.filesConsidered} · erros sintáticos ${it.astReport.filesWithSyntaxErrors} · observações ${it.findings.size}") }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("02 · Auditoria binária VislumbOps", style = MaterialTheme.typography.titleMedium)
                    Text(apkName, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { apkPicker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream")) }, enabled = !audit.running) { Text("Selecionar APK") }
                        if (audit.running) OutlinedButton(onClick = { verificationVm.cancel() }) { Text("Cancelar") }
                    }
                    if (audit.running) Text("Auditoria em curso: ${audit.phase}")
                    audit.report?.let { report ->
                        Text("Resultado: ${report.contract.status} · ${report.contract.findings.size} achados · SHA-256 ${report.contract.artifactHash.take(20)}…")
                        Button(onClick = {
                            val current = case
                            if (current?.phase == WorkflowPhase.REPAIR_APPLIED) vm.recordProfessionalReaudit(report.contract)
                            else vm.assessProfessionalWorkflow(report.contract)
                        }, enabled = !busy && !audit.running && case != null &&
                            (case?.phase != WorkflowPhase.REPAIR_PREPARED) && (case?.phase != WorkflowPhase.REAUDIT_COMPLETE) && workflowAuditReady &&
                            (case?.phase != WorkflowPhase.REPAIR_APPLIED || reAuditReady)) {
                            Text(if (case?.phase == WorkflowPhase.REPAIR_APPLIED) "Registar nova auditoria cruzada" else "Consolidar diagnóstico e evidências")
                        }
                    }
                    audit.error?.let { Text("Falha de auditoria: $it", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        case?.let { active ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("03 · Caso e trilho de auditoria", style = MaterialTheme.typography.titleMedium)
                        Text("ID: ${active.id}")
                        Text("Estado: ${active.phase}", color = MaterialTheme.colorScheme.primary)
                        Text("Build: ${active.buildReference} · Eventos: ${active.eventCount}")
                        Text("Workspace: ${active.workspacePrefix}")
                        Text("Fingerprint de conteúdo: ${active.sourceSha256}", style = MaterialTheme.typography.bodySmall)
                        active.binaryArtifactSha256?.let { Text("APK SHA-256: $it", style = MaterialTheme.typography.bodySmall) }
                        active.assessmentSha256?.let { Text("Evidência SHA-256: $it", style = MaterialTheme.typography.bodySmall) }
                        Text(active.lastMessage)
                        Text("Cabeça do diário: ${active.journalHeadSha256}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        assessment?.let { result ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("04 · Avaliação cruzada", style = MaterialTheme.typography.titleMedium)
                        Text("${result.findings.size} achados · ${result.checks.size} verificações · revisão necessária: ${result.requiresReview}")
                        Text("Snapshot ZIP SHA-256: ${result.sourceArchiveSha256}", style = MaterialTheme.typography.bodySmall)
                        Text("APK SHA-256: ${result.binaryArtifactSha256}", style = MaterialTheme.typography.bodySmall)
                        Text("AST: ${result.astFilesParsed}/${result.astFilesConsidered} · indisponíveis ${result.astFilesUnavailable} · erros sintáticos ${result.astFilesWithSyntaxErrors}")
                        Text("Proveniência: não verificada dentro da aplicação. A referência de build não comprova a relação entre ZIP e APK; consulte docs/BUILD_ATTESTATION.md.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            item { Text("Achados consolidados", style = MaterialTheme.typography.titleMedium) }
            items(result.findings) { finding ->
                ItemCard("${finding.severity} · ${finding.title}", "${finding.description}\n${finding.location.orEmpty()}\nAção sugerida: ${finding.remediationHint.orEmpty()}")
            }
            items(result.limitations) { limitation -> Text("Limitação: $limitation", style = MaterialTheme.typography.bodySmall) }
        }
        if (case?.phase in setOf<WorkflowPhase?>(WorkflowPhase.ASSESSED, WorkflowPhase.NEEDS_REVIEW) && assessment != null) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("05 · Preparar correção controlada", style = MaterialTheme.typography.titleMedium)
                        Text("Nenhuma correção é aplicada automaticamente. Pode selecionar um ficheiro corrigido ou gerar uma proposta no ecrã Omega e enviá-la para este mesmo gate. O destino tem de existir no workspace Espelhar-X; o código original da proposta Omega é comparado exatamente com o ficheiro atual.", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(target, { target = it }, label = { Text("Caminho relativo no workspace (ex.: projects/my-app/app/src/main/AndroidManifest.xml)") }, modifier = Modifier.fillMaxWidth())
                        OutlinedButton(onClick = { replacementPicker.launch(arrayOf("*/*")) }, enabled = target.isNotBlank() && !busy) { Text("Selecionar ficheiro corrigido e preparar plano") }
                    }
                }
            }
        }
        if (case?.phase == WorkflowPhase.REPAIR_PREPARED) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("06 · Aprovação explícita", style = MaterialTheme.typography.titleMedium)
                        Text("Plano: ${case?.repairPlanId}\nDestino: ${case?.repairTarget}\nNovo SHA-256: ${case?.repairReplacementSha256}")
                        Text("A aprovação substitui um único ficheiro após revalidar o hash atual e guardar backup verificado.")
                        Button(onClick = { confirmRepair = true }, enabled = !busy) { Text("Aprovar e aplicar correção") }
                    }
                }
            }
        }
        if (case?.phase == WorkflowPhase.REPAIR_APPLIED) {
            item {
                ItemCard("07 · Reconstrução obrigatória", "A correção foi aplicada. Reconstrua o projeto num ambiente de build confiável. Depois exporte o snapshot atualizado do mesmo workspace, indique uma referência nova e audite o APK reconstruído. O HubOps não presume proveniência sem atestação de build.")
                Text("Atualize também a referência da nova build no campo acima; tem de ser diferente da anterior.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { workflowAuditReady = false; vm.beginProfessionalWorkflow(workspacePrefix, buildRef.trim(), reAudit = true) }, enabled = !busy && !reAuditReady && workspacePrefix.isNotBlank() && buildRef.isNotBlank()) { Text(if (reAuditReady) "Snapshot atualizado pronto · selecione o APK" else "Exportar snapshot atualizado para reauditoria") }
                OutlinedButton(onClick = { confirmRollback = true }, enabled = !busy) { Text("Reverter última correção com backup") }
            }
        }
        if (case?.phase == WorkflowPhase.REAUDIT_COMPLETE) {
            item { ItemCard("08 · Ciclo concluído", "A nova auditoria foi aprovada e o caso está encerrado. O diário mantém a cadeia de eventos e os hashes de evidência.") }
        }
        if (audit.log.isNotEmpty()) {
            item { Text("Registo VislumbOps", style = MaterialTheme.typography.titleMedium) }
            items(audit.log.takeLast(12)) { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        item { StatusLines(vm) }
    }
    if (confirmRollback) AlertDialog(
        onDismissRequest = { confirmRollback = false },
        title = { Text("Reverter correção") },
        text = { Text("O motor só reverterá se o ficheiro de destino ainda corresponder exatamente ao hash aplicado e o backup estiver íntegro. Nenhum ficheiro será apagado automaticamente.") },
        confirmButton = { TextButton(onClick = { confirmRollback = false; vm.rollbackProfessionalRepair() }) { Text("Reverter") } },
        dismissButton = { TextButton(onClick = { confirmRollback = false }) { Text("Cancelar") } }
    )
    if (confirmRepair) AlertDialog(
        onDismissRequest = { confirmRepair = false },
        title = { Text("Confirmar reparação") },
        text = { Text("Autoriza a substituição do ficheiro ${case?.repairTarget}? Será criado um backup e o hash do destino será novamente verificado antes da escrita.") },
        confirmButton = { TextButton(onClick = { confirmRepair = false; vm.approveProfessionalRepair() }) { Text("Aprovar") } },
        dismissButton = { TextButton(onClick = { confirmRepair = false }) { Text("Cancelar") } }
    )
}
