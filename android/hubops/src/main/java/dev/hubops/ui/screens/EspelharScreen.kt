package dev.hubops.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.hubops.ui.HubViewModel

@Composable
fun EspelharScreen(vm: HubViewModel) {
    val analysis by vm.espelharAnalysis.collectAsState()
    val pipeline by vm.espelharPipeline.collectAsState()
    val snapshotDiff by vm.espelharSnapshotDiff.collectAsState()
    val appliedRepairs by vm.espelharAppliedRepairs.collectAsState()
    val output by vm.espelharOutput.collectAsState()
    val loaded by vm.espelharLoaded.collectAsState()
    val generatedProject by vm.generatedProject.collectAsState()
    val integrationProposal by vm.integrationProposal.collectAsState()
    val integrationOutcome by vm.integrationOutcome.collectAsState()
    val integrationAudit by vm.integrationAudit.collectAsState()
    val registeredProjects by vm.registeredProjects.collectAsState()
    val projectInsight by vm.projectInsight.collectAsState()
    var path by rememberSaveable { mutableStateOf("main.py") }
    var code by remember { mutableStateOf("# HubOps · Espelhar-X\n# Análise estática local; guardar no workspace privado.\n\ndef exemplo(valor=None):\n    return valor\n") }
    var mode by rememberSaveable { mutableStateOf("editor") }
    var githubRepo by rememberSaveable { mutableStateOf("") }
    var githubRef by rememberSaveable { mutableStateOf("") }
    var projectPrefix by rememberSaveable { mutableStateOf("projects/despertar") }
    LaunchedEffect(loaded) { loaded?.let { (name, source) -> path = name; code = source } }
    LaunchedEffect(path, code) { delay(450); if (path.isNotBlank()) vm.analyzeEspelhar(path, code) }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ScreenTitle("Espelhar-X · IDE integrada") }
        item { Text("Módulo nativo do HubOps: editor, análise estática incremental, símbolos, pipeline e execução controlada. Funciona offline e partilha o workspace privado da aplicação.") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { mode = "editor" }) { Text("Editor") }
                Button(onClick = { mode = "pipeline" }) { Text("Pipeline") }
                Button(onClick = { mode = "run" }) { Text("Execução") }
            }
        }
        if (mode == "editor") {
            item {
                OutlinedTextField(value = path, onValueChange = { path = it }, label = { Text("Ficheiro (ex.: src/main.py)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item {
                OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Código-fonte") }, modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp), minLines = 12, textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace))
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.analyzeEspelhar(path, code) }) { Text("Analisar") }
                    Button(onClick = { vm.saveEspelharFile(path, code) }) { Text("Guardar") }
                    Button(onClick = { vm.loadEspelharFile(path) }) { Text("Abrir") }
                }
            }
            analysis?.let { result ->
                item { ItemCard("${result.language} · ${result.file}", "${result.errors} erros · ${result.warnings} avisos · ${result.latencyMs} ms${if (result.cached) " · cache" else ""} · ${if (result.syntaxOk) "sem erro estrutural detetado" else "estrutura com problemas"}") }
                items(result.issues) { issue -> ItemCard("${issue.severity.uppercase()} · L${issue.line} · ${issue.type}", issue.message) }
                item { Text("Símbolos (${result.symbols.size})") }
                items(result.symbols) { symbol -> Text("${symbol.type}  ${symbol.name}  · L${symbol.line}", fontFamily = FontFamily.Monospace) }
                if (result.issues.isEmpty()) item { Text("Nenhum problema heurístico detetado. Isto não substitui compilador, linter ou parser completo.") }
            }
        } else if (mode == "pipeline") {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.runEspelharPipeline() }) { Text("Analisar workspace") }
                    Button(onClick = { vm.prepareGeneratedProjectTransfer() }, enabled = generatedProject != null) {
                        Text("Preparar integração do Despertar")
                    }
                }
            }
            item { OutlinedTextField(value = githubRepo, onValueChange = { githubRepo = it }, label = { Text("Repositório GitHub (owner/repo)") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = githubRef, onValueChange = { githubRef = it }, label = { Text("Branch/ref (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.compareGitHubRepositoryWithWorkspace(githubRepo, githubRef) }, enabled = githubRepo.contains("/")) { Text("Comparar") }
                    Button(onClick = { vm.importGitHubRepositoryToEspelhar(githubRepo, githubRef) }, enabled = githubRepo.contains("/")) { Text("Importar snapshot") }
                }
            }
            item { Text("GitHub: compara primeiro o snapshot remoto com a cópia local sem alterar ficheiros. A importação continua separada e nunca substitui ficheiros existentes.") }
            snapshotDiff?.let { diff ->
                item { ItemCard("Comparação · ${diff.added.size} novos · ${diff.changed.size} alterados", "${diff.unchanged.size} iguais · ${diff.localOnly.size} apenas locais. Comparação somente-leitura; nada foi modificado.") }
                items(diff.added) { path -> ItemCard("NOVO · $path", "Existe no snapshot remoto, mas não na cópia local.") }
                items(diff.changed) { path -> ItemCard("ALTERADO · $path", "O conteúdo remoto difere da cópia local.") }
                items(diff.localOnly) { path -> ItemCard("APENAS LOCAL · $path", "Existe localmente, mas não no snapshot remoto.") }
            }
            item { Text("Integração governada: os motores permanecem autónomos. Toda a transferência regista origem e hash, apresenta impacto, entra primeiro em staging e só é promovida após validação.") }
            item {
                OutlinedTextField(value = projectPrefix, onValueChange = { projectPrefix = it },
                    label = { Text("Pasta do projeto para avaliação pelo Despertar") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { vm.inspectEspelharProjectInDespertar(projectPrefix) }, enabled = projectPrefix.isNotBlank()) {
                    Text("Enviar snapshot para análise no Despertar")
                }
            }
            projectInsight?.let { insight ->
                item { ItemCard("Despertar · inspeção somente-leitura", "${insight.filesScanned} ficheiros · ${insight.sourceFiles} fontes · ${insight.kotlinFiles} Kotlin · ${insight.javaFiles} Java · ${insight.pythonFiles} Python · ${insight.findings.size} observações. Nenhuma alteração foi aplicada.") }
                items(insight.findings) { finding -> ItemCard("${finding.severity} · ${finding.title}", finding.detail) }
                item { Text("AST partilhada: ${insight.astReport.filesParsed} analisados · ${insight.astReport.filesUnavailable} indisponíveis · ${insight.astReport.filesWithSyntaxErrors} com erros sintáticos.") }
            }
            integrationProposal?.let { proposal ->
                item { ItemCard("Proposta ${proposal.status} · ${proposal.provenance.sourceEngine} → ${proposal.destinationEngine}", "Artefacto: ${proposal.provenance.artifactName}\nSHA-256: ${proposal.provenance.artifactSha256}\nDestino: ${proposal.impact.destinationPrefix}\nStaging: integration-staging/${proposal.id}\nFicheiros suportados: ${proposal.impact.supportedFiles} · ignorados: ${proposal.impact.ignoredFiles} · bytes expandidos: ${proposal.impact.expandedBytes}\nColisões: ${proposal.impact.collisions.size}") }
                items(proposal.impact.collisions) { collision -> ItemCard("COLISÃO · $collision", "A transferência não será autorizada enquanto existir um ficheiro no destino.") }
                items(proposal.impact.riskNotes) { risk -> Text("• $risk") }
                // HubEnd: os botões estavam fora de item {} dentro do LazyColumn (erro de compilação Compose).
                if (proposal.status.name == "PREPARED") {
                    item {
                        Button(onClick = { vm.approveGeneratedProjectTransfer() }, enabled = proposal.impact.collisions.isEmpty() && vm.canApproveGeneratedProjectTransfer(proposal.id)) {
                            Text("Confirmar transferência e validar")
                        }
                    }
                }
                item { Button(onClick = { vm.cancelIntegrationProposal(proposal.id) }) { Text("Cancelar proposta") } }
            }
            integrationOutcome?.let { outcome ->
                item { ItemCard(if (outcome.accepted) "INTEGRAÇÃO ACEITE" else "INTEGRAÇÃO NÃO ACEITE · ${outcome.proposal.status}", "${outcome.message}\nImportados em staging: ${outcome.importedFiles} · promovidos: ${outcome.promotedFiles}\nValidação: ${outcome.validation.filesChecked} ficheiros · ${outcome.validation.errors} erros · ${outcome.validation.warnings} avisos\nAST: ${outcome.validation.astParsedFiles}/${outcome.validation.astExpectedFiles} analisados · indisponíveis: ${outcome.validation.astUnavailableFiles} · erros sintáticos: ${outcome.validation.astSyntaxErrorFiles} · truncados: ${outcome.validation.astTruncatedFiles}\nEvidência SHA-256: ${outcome.validation.evidenceSha256}") }
                items(outcome.validation.limitations) { limitation -> Text("Limitação: $limitation") }
            }
            if (registeredProjects.isNotEmpty()) {
                item { Text("Project Registry · projetos aceites") }
                items(registeredProjects, key = { it.proposalId }) { record ->
                    ItemCard("${record.sourceEngine} → ${record.destinationEngine} · ${record.acceptedFiles} ficheiros",
                        "${record.artifactName}\nDestino: ${record.destinationPrefix}\nOrigem SHA-256: ${record.artifactSha256}\nValidação SHA-256: ${record.validationEvidenceSha256}")
                }
            }
            if (integrationAudit.isNotEmpty()) {
                item { Text("Integration Ledger · histórico persistente") }
                items(integrationAudit) { line -> Text(line, fontFamily = FontFamily.Monospace) }
            }
            if (appliedRepairs.isNotEmpty()) {
                item { Text("Reparações aplicadas · reversão disponível nesta sessão") }
                items(appliedRepairs, key = { "applied-${it.path}" }) { repair ->
                    ItemCard("ALTERAÇÃO APLICADA · ${repair.path}", "${repair.title}. A reversão só será permitida se o ficheiro ainda corresponder exatamente ao conteúdo aplicado e o backup original estiver íntegro.")
                    Button(onClick = { vm.rollbackEspelharRepair(repair) }) { Text("Reverter com verificação") }
                }
            }
            pipeline?.let { report ->
                item { ItemCard("${report.filesChecked} ficheiros · ${report.errors} erros · ${report.warnings} avisos", "Análise local; ficheiros não são executados nem modificados.") }
                items(report.tasks) { task -> ItemCard("${task.status.uppercase()} · ${task.name}", task.detail) }
                report.projectGraph?.let { graph ->
                    item { Text("Mapa de dependências") }
                    item { ItemCard("${graph.files.size} ficheiros · ${graph.resolved} associados · ${graph.unresolved} não resolvidos · ${graph.ambiguous} ambíguos", "Associação estática de imports e packages; não substitui o compilador.") }
                    items(graph.edges) { edge -> ItemCard("${edge.status.uppercase()} · ${edge.importedName}", "${edge.from}${if (edge.candidates.isEmpty()) "" else " → ${edge.candidates.joinToString()}"}") }
                }
                report.buildReadiness?.let { readiness ->
                    item { Text("Build Intelligence · pré-verificação") }
                    item { ItemCard("${readiness.status.uppercase()} · ${readiness.sourceFiles} fontes", "Manifesto: ${readiness.androidManifest} · build raiz: ${readiness.rootBuildFile} · settings: ${readiness.settingsFile} · app: ${readiness.appBuildFile} · wrapper: script=${readiness.wrapperScript}, JAR=${readiness.wrapperJar}, propriedades=${readiness.wrapperProperties}") }
                    items(readiness.blockers) { issue -> ItemCard("BLOQUEIO", issue) }
                    items(readiness.warnings) { issue -> ItemCard("AVISO", issue) }
                    item { Text("Esta verificação é estática. Não confirma SDK instalado, resolução de dependências, compilação nem geração de APK.") }
                }
                report.repairs?.let { repairs ->
                    item { Text("Repair Engine · propostas para revisão") }
                    item { ItemCard("${repairs.candidates.size} propostas", "Nenhuma alteração é aplicada automaticamente. Cada proposta verifica o hash original e cria backup antes de substituir.") }
                    items(repairs.candidates) { repair ->
                        ItemCard("${repair.title} · ${repair.path}", "${repair.detail} · ID: ${repair.id}")
                        Button(onClick = { vm.applyEspelharRepair(repair) }) { Text("Aplicar com backup") }
                    }
                }
                report.ast?.let { ast ->
                    item { Text("Árvore sintática real · Tree-sitter") }
                    item { ItemCard("${ast.filesParsed}/${ast.filesConsidered} ficheiros analisados", "${ast.totalNodes} nós · ${ast.declarations.size} declarações · ${ast.filesWithSyntaxErrors} ficheiros com erros sintáticos") }
                    if (ast.declarations.isNotEmpty()) {
                        item { Text("Declarações encontradas") }
                        items(ast.declarations) { declaration ->
                            Text("${declaration.kind}  ${declaration.name}  · ${declaration.path}:${declaration.line}", fontFamily = FontFamily.Monospace)
                        }
                    }
                    ast.unavailableReasons.forEach { reason -> item { ItemCard("Parser indisponível", reason) } }
                    if (ast.truncatedFiles > 0 || ast.filesOmitted > 0) {
                        item { ItemCard("Limites aplicados", "${ast.truncatedFiles} ficheiros truncados por limite de nós · ${ast.filesOmitted} ficheiros omitidos por limite de volume.") }
                    }
                }
            }
        } else {
            item { Text("A execução está disponível apenas para scripts Shell (.sh). Corre como o UID da aplicação, na pasta privada; não é uma sandbox forte, não usa proot e não concede root. Não execute código de terceiros sem o rever.") }
            item { Button(onClick = { vm.runEspelharShell(code) }, enabled = code.isNotBlank()) { Text("Executar como Shell") } }
            output?.let { result ->
                item { ItemCard("Saída · código ${result.exitCode}", "${if (result.timedOut) "Tempo esgotado" else "Concluído"}${if (result.truncated) " · saída truncada" else ""}") }
                item { Text(result.output.ifBlank { "(sem saída)" }, fontFamily = FontFamily.Monospace) }
            }
        }
        item { StatusLines(vm) }
    }
}
