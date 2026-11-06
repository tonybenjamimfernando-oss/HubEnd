package com.hubops.vislumbrops.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hubops.vislumbrops.core.VerificationScore
import com.hubops.vislumbrops.ui.theme.HubOpsTheme

@Composable
fun VerificationScreen(vm: VerificationViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var projectId by remember { mutableStateOf("project-001") }
    var selectedName by remember { mutableStateOf("Nenhum APK selecionado") }

    val manifestSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportSupplyChainManifest(context, uri)
    }
    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedName = uri.lastPathSegment ?: "APK selecionado"
            vm.startFromUri(context, projectId.trim().ifBlank { "project-unknown" }, uri)
        }
    }

    HubOpsTheme {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("VISLUMBROPS", fontSize = 27.sp)
            Text("Auditoria · Cadeia de fornecimento · Evidências · Certificação",
                color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp)

            OutlinedTextField(
                value = projectId,
                onValueChange = { projectId = it },
                label = { Text("Identificador do projeto") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Text(selectedName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { apkPicker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream")) },
                    enabled = !state.running) { Text("Selecionar APK") }
                OutlinedButton(onClick = { vm.cancel() }, enabled = state.running) { Text("Cancelar") }
            }

            if (state.running) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (state.phase.isNotEmpty()) {
                Text("Fase: ${state.phase}", color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp)
            }

            state.report?.let { report ->
                val contract = report.contract
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val score = contract.score
                        if (score != null) {
                            Text("Score: ${score.overall}/100 — ${score.grade}",
                                fontSize = 20.sp, color = gradeColor(score.grade))
                            Text("Segurança ${score.security} · Estabilidade ${score.stability} · Performance ${score.performance} · Conformidade ${score.compliance}",
                                fontSize = 11.sp)
                        }
                        Text("Estado: ${contract.status} · Achados: ${contract.findings.size}", fontSize = 13.sp)
                        Text("SHA-256: ${contract.artifactHash.take(32)}…", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        contract.metadata["supplyChain.manifestSha256"]?.let {
                            Text("Manifesto da cadeia: ${it.take(32)}…", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        }
                        Text("Inventário: ${contract.metadata["supplyChain.entryCount"] ?: "indisponível"} entradas",
                            fontSize = 12.sp)
                        contract.certificate?.let {
                            Text("Atestado local: ${it.certificateId} · ${it.grade}",
                                fontSize = 11.sp, color = Color(0xFF00E5FF))
                            Text("Assinatura local Android Keystore; não é certificado público.", fontSize = 10.sp)
                        }
                    }
                }
                OutlinedButton(
                    onClick = { manifestSaver.launch("vislumbrops-supply-chain-manifest.json") },
                    enabled = !state.running && contract.metadata["supplyChain.manifestSha256"] != null
                ) { Text("Exportar manifesto JSON") }
                if (contract.findings.isNotEmpty()) {
                    Text("Principais achados", style = MaterialTheme.typography.titleSmall)
                    contract.findings.take(5).forEach { finding ->
                        Text("• [${finding.severity}] ${finding.title}", fontSize = 12.sp)
                    }
                }
            }

            state.error?.let { Text("Erro: $it", color = MaterialTheme.colorScheme.error) }

            Text("Registo de execução", style = MaterialTheme.typography.titleSmall)
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth()
                    .background(Color(0xFF111118))
                    .padding(8.dp)
            ) {
                items(state.log) { line ->
                    Text(line, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
        }
    }
}

private fun gradeColor(grade: VerificationScore.Grade): Color = when (grade) {
    VerificationScore.Grade.GOLD -> Color(0xFFFFD700)
    VerificationScore.Grade.SILVER -> Color(0xFFC0C0C0)
    VerificationScore.Grade.BRONZE -> Color(0xFFCD7F32)
    VerificationScore.Grade.REJECTED -> Color(0xFFE53935)
}
