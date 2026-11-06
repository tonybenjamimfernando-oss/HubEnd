package dev.hubops.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import dev.hubops.despertar.LicensePolicy
import dev.hubops.ui.HubViewModel

@Composable
fun DespertarScreen(vm: HubViewModel) {
    val inspirations by vm.inspirations.collectAsState()
    val study by vm.repositoryStudy.collectAsState()
    val stagedApk by vm.stagedApk.collectAsState()
    val termuxResult by vm.termuxResult.collectAsState()
    val generatedProject by vm.generatedProject.collectAsState()
    val buildArtifactPath by vm.buildArtifactPath.collectAsState()
    val repairFindings by vm.buildRepairFindings.collectAsState()
    val projectInsight by vm.projectInsight.collectAsState()
    val architectureReport by vm.architectureReport.collectAsState()
    val fusionSource by vm.fusionSource.collectAsState()
    val fusionReport by vm.fusionReport.collectAsState()
    val codeSimilarityReport by vm.codeSimilarityReport.collectAsState()
    val busy by vm.busy.collectAsState()
    LaunchedEffect(termuxResult?.executionId, termuxResult?.isPending) {
        // Não faz polling quando não existe uma tarefa Termux pendente.
        if (termuxResult?.isPending == true) {
            while (true) {
                delay(1500)
                vm.refreshTermuxResult()
            }
        }
    }
    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importApk)
    }
    val fusionPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::selectFusionSource)
    }
    val clipboard = LocalClipboardManager.current
    val uri = LocalUriHandler.current
    val context = LocalContext.current
    var owner by rememberSaveable { mutableStateOf("") }
    var repo by rememberSaveable { mutableStateOf("") }
    var keywords by rememberSaveable { mutableStateOf("") }
    var authorized by rememberSaveable { mutableStateOf(false) }
    var outName by rememberSaveable { mutableStateOf("") }
    var projectName by rememberSaveable { mutableStateOf("Minha Aplicacao") }
    var projectPackage by rememberSaveable { mutableStateOf("com.exemplo.minhaapp") }
    var projectInternet by rememberSaveable { mutableStateOf(false) }
    val unlocked = vm.entitlement.isUnlocked()

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { ScreenTitle("Despertar") }
        if (!unlocked) {
            item { Text("Recurso pago, bloqueado. (Em desenvolvimento: ative em Config.)") }
        }
        item {
            Text(
                "Estuda repositórios públicos como inspiração, mostra a licença de cada um e grava " +
                    "ATTRIBUTIONS.md num ramo do SEU repositório. Não copia código de terceiros.",
            )
        }
        item {
            ScreenTitle("Project Forge · Gerar projeto Android")
            Text("Cria um projeto Kotlin + Jetpack Compose independente, pronto para abrir no Android Studio. O ZIP é gerado localmente; nenhum código é enviado para servidores.")
            OutlinedTextField(
                value = projectName, onValueChange = { projectName = it },
                label = { Text("Nome da aplicação") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = projectPackage, onValueChange = { projectPackage = it },
                label = { Text("Package / Application ID") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = projectInternet, onCheckedChange = { projectInternet = it })
                Text("Adicionar permissão de Internet")
            }
            Button(
                onClick = { vm.generateAndroidProject(projectName, projectPackage, projectInternet) },
                enabled = !busy && unlocked && projectName.isNotBlank() && projectPackage.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Gerar projeto Android ZIP") }
            generatedProject?.let { file ->
                Text("Projeto criado: ${file.name} · ${file.length() / 1024} KB")
                OutlinedButton(
                    onClick = { vm.analyzeGeneratedProject() },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Analisar projeto · Code Intelligence") }
                OutlinedButton(
                    onClick = { vm.architectGeneratedProject() },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Arquitetar projeto · Project Architect") }
                architectureReport?.let { report ->
                    ScreenTitle("Project Architect · mapa e plano")
                    Text("Módulos: ${report.moduleNames.joinToString().ifBlank { "não identificados" }}")
                    Text("${report.packageCount} packages · ${report.sourceCount} fontes · ${report.internalImportCount} imports internos · ${report.externalImportCount} externos")
                    if (report.packageDependencies.isNotEmpty()) {
                        Text("Dependências entre packages:")
                        report.packageDependencies.forEach { Text("• $it") }
                    }
                    report.findings.forEach { Text("${it.severity} · ${it.title}: ${it.detail}") }
                    Text("Plano recomendado:")
                    report.implementationPlan.forEachIndexed { index, step -> Text("${index + 1}. $step") }
                    OutlinedButton(onClick = {
                        clipboard.setText(AnnotatedString(report.toMarkdown()))
                    }, modifier = Modifier.fillMaxWidth()) { Text("Copiar plano arquitetural") }
                }
                ScreenTitle("Component Fusion · comparar projetos")
                Text("Selecione um segundo ZIP Android para comparar ficheiros, packages, dependências Gradle e permissões. Esta etapa prepara um plano; não copia nem modifica código.")
                OutlinedButton(
                    onClick = { fusionPicker.launch(arrayOf("application/zip", "application/octet-stream")) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Selecionar ZIP de referência") }
                fusionSource?.let { Text("Referência carregada: ${it.name} · ${it.length() / 1024} KB") }
                Button(
                    onClick = { vm.analyzeComponentFusion() },
                    enabled = !busy && unlocked && fusionSource != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Comparar e preparar plano de fusão") }
                Button(
                    onClick = { vm.compareCodeSimilarity() },
                    enabled = !busy && unlocked && fusionSource != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Medir similaridade das fontes") }
                codeSimilarityReport?.let { report ->
                    ScreenTitle("Code Similarity & Refactoring Lab")
                    Text("Similaridade textual: ${report.textualPercent}%")
                    Text("Similaridade estrutural lexical: ${report.structuralPercent}%")
                    Text("${report.comparedFiles} ficheiros comparados · ${report.movedOrRenamedFiles} movidos/renomeados emparelhados · ${report.unmatchedOriginal} originais e ${report.unmatchedReplica} réplicas sem par")
                    Text("Refatoração: ${report.refactoringStatus}")
                    Text("Procura de pares: ${report.matchingComparisons} comparações${if (report.matchingSearchTruncated) " · limite atingido" else ""} · ${report.truncatedFileCount} ficheiros truncados · ${report.truncatedSourceFiles} fontes omitidas")
                    report.files.take(30).forEach { item ->
                        Text("${item.path}${if (item.path != item.replicaPath) " → ${item.replicaPath} (${item.matchType})" else ""} · texto ${item.textualPercent}% · estrutura ${item.structuralPercent}%")
                    }
                    report.limitations.forEach { Text("• $it") }
                    OutlinedButton(onClick = {
                        clipboard.setText(AnnotatedString(report.toMarkdown()))
                    }, modifier = Modifier.fillMaxWidth()) { Text("Copiar relatório de similaridade") }
                }
                fusionReport?.let { report ->
                    ScreenTitle("Relatório Component Fusion")
                    Text("${report.primaryFiles} ficheiros no projeto principal · ${report.referenceFiles} no projeto de referência")
                    Text("Colisões de caminho: ${report.exactPathConflicts.size} · Nomes repetidos: ${report.sourceNameConflicts.size} · Packages partilhados: ${report.packageOverlaps.size}")
                    Text("Conflitos de dependências: ${report.dependencyConflicts.size} · Diferenças de permissões: ${report.permissionDifferences.size}")
                    report.findings.forEach { finding -> Text("${finding.severity} · ${finding.title}: ${finding.detail}") }
                    ScreenTitle("Plano de integração")
                    report.integrationPlan.forEachIndexed { index, step -> Text("${index + 1}. $step") }
                    OutlinedButton(onClick = {
                        clipboard.setText(AnnotatedString(report.toMarkdown()))
                    }, modifier = Modifier.fillMaxWidth()) { Text("Copiar relatório Component Fusion") }
                }
                projectInsight?.let { insight ->
                    ScreenTitle("Code Intelligence · relatório estrutural")
                    Text("${insight.filesScanned} ficheiros · ${insight.sourceFiles} fontes · ${insight.kotlinFiles} Kotlin · ${insight.javaFiles} Java · ${insight.pythonFiles} Python")
                    Text("Testes identificados: ${insight.testFiles} · TODO/FIXME: ${insight.todoCount}")
                    ScreenTitle("AST real · Tree-sitter")
                    Text("${insight.astReport.filesParsed}/${insight.astReport.filesConsidered} ficheiros analisados · ${insight.astReport.filesWithSyntaxErrors} com erros sintáticos · ${insight.astReport.truncatedFiles} limitados · ${insight.astReport.totalNodes} nós · ${insight.astReport.declarations.size} declarações estruturais")
                    insight.astReport.fileResults.take(12).forEach { parsed ->
                        Text("${parsed.path} · ${parsed.language} · ${parsed.nodeCount} nós${if (parsed.hasSyntaxErrors) " · ERROS SINTÁTICOS" else " · sem erros detetados"}${if (parsed.truncated) " · LIMITE" else ""}")
                        parsed.declarations.take(8).forEach { declaration ->
                            Text("  ${declaration.kind}: ${declaration.name} · linha ${declaration.line}")
                        }
                    }
                    insight.astReport.unavailableReasons.take(6).forEach { Text("Parser indisponível: $it") }
                    Text("AST sintática não substitui resolução semântica, compilação ou testes.")
                    ScreenTitle("Índice de símbolos · análise lexical")
                    Text("${insight.symbolIndex.filesIndexed} ficheiros indexados · ${insight.symbolIndex.declarationsFound} declarações · ${insight.symbolIndex.referenceOccurrences} ocorrências · ${insight.symbolIndex.ambiguousNames} nomes ambíguos")
                    insight.symbolIndex.symbols.take(20).forEach { symbol ->
                        Text("${symbol.name} · ${symbol.kinds.joinToString()} · ${symbol.referenceCount} ocorrências · ${symbol.declarationPaths.joinToString()}${if (symbol.ambiguous) " · AMBÍGUO" else ""}")
                    }
                    Text("Este índice não é AST nem resolução semântica; não aplica renomeações automáticas.")
                    insight.findings.forEach { finding ->
                        Text("${finding.severity} · ${finding.title}: ${finding.detail}")
                    }
                    OutlinedButton(onClick = {
                        clipboard.setText(AnnotatedString(insight.toMarkdown()))
                    }, modifier = Modifier.fillMaxWidth()) { Text("Copiar relatório Code Intelligence") }
                }
                OutlinedButton(onClick = {
                    val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, contentUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(send, "Guardar ou partilhar projeto Android"))
                }, modifier = Modifier.fillMaxWidth()) { Text("Guardar / partilhar ZIP") }
                Button(
                    onClick = { vm.buildGeneratedAndroidProject() },
                    enabled = !busy && unlocked && termuxResult?.isPending != true,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Compilar projeto e gerar APK no Termux") }
                Text("Requisitos: Termux, JDK 17, Gradle 8.11.1–8.14.3, unzip, Android SDK Platform 34 + Build Tools 34.0.0 (aapt/apksigner) e armazenamento autorizado.")
                buildArtifactPath?.let {
                    Text("APK validado e produzido: $it")
                    Text("O Termux também guardou um relatório de compilação com package, SHA-256 e verificação da assinatura.")
                }
            }
        }
        item {
            OutlinedTextField(
                value = owner, onValueChange = { owner = it }, label = { Text("Dono do repositório") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = repo, onValueChange = { repo = it }, label = { Text("Repositório") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = keywords, onValueChange = { keywords = it },
                label = { Text("Palavras-chave de inspiração") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = authorized, onCheckedChange = { authorized = it })
                Text("Tenho autorização sobre o repositório/APK selecionado")
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.despertarCheck(owner, repo, authorized) }) { Text("Verificar acesso") }
                Button(onClick = { vm.despertarInspire(owner, repo, keywords, authorized) }) { Text("Pesquisar") }
            }
        }
        item {
            Button(onClick = { vm.despertarInspect(owner, repo) }, modifier = Modifier.fillMaxWidth()) {
                Text("Análise técnica profunda do repositório")
            }
        }
        study?.let { result ->
            item {
                ScreenTitle("Relatório técnico")
                Text("${result.fullName} · ${result.language.ifBlank { "Linguagem n/d" }} · ${result.license.ifBlank { "Sem licença identificada" }}")
                Text("★ ${result.stars}  ·  Forks ${result.forks}  ·  Issues ${result.openIssues}")
                Text("Ramo: ${result.defaultBranch} · Arquivado: ${if (result.archived) "Sim" else "Não"}")
                if (result.topics.isNotEmpty()) Text("Tópicos: ${result.topics.joinToString()}")
                if (result.languages.isNotEmpty()) Text("Linguagens: ${result.languages.entries.sortedByDescending { it.value }.joinToString { it.key }}")
                Text("Licença: ${result.licenseClass.label}. ${LicensePolicy.guidance(result.licenseClass)}")
                ScreenTitle("Auditoria estrutural")
                Text("Código: ${result.sourceFileCount} ficheiros · Testes: ${result.testFileCount} · Workflows CI: ${result.workflowCount}")
                Text("Política de segurança: ${if (!result.treeAvailable) "Não avaliada" else if (result.securityPolicyFound) "Encontrada" else "Não localizada"}")
                if (!result.treeAvailable) Text("A API não disponibilizou a árvore de ficheiros; as contagens estruturais não são conclusivas.")
                if (result.treeTruncated) Text("A API limitou a árvore do repositório; as contagens são parciais.")
                if (result.buildSystems.isNotEmpty()) Text("Build: ${result.buildSystems.joinToString()}")
                if (result.dependencyManifests.isNotEmpty()) Text("Manifestos: ${result.dependencyManifests.joinToString()}")
                ScreenTitle("Revisão estática heurística (${result.filesReviewed} ficheiros)")
                result.codeFindings.forEach { Text("• $it") }
                if (result.rootFiles.isNotEmpty()) Text("Ficheiros de raiz: ${result.rootFiles.joinToString()}")
                ScreenTitle("Atividade recente")
                result.recentCommits.forEach { Text("• $it") }
                Text("Última release: ${result.latestRelease.ifBlank { "Não encontrada" }}")
                if (result.readmeExcerpt.isNotBlank()) Text("README:\n${result.readmeExcerpt.take(1800)}")
                OutlinedButton(onClick = { vm.exportStudyReport()?.let { clipboard.setText(AnnotatedString(it)) } }) {
                    Text("Copiar relatório Markdown")
                }
                OutlinedButton(onClick = { uri.openUri(result.url) }) { Text("Abrir repositório") }
            }
        }
        item {
            Button(onClick = { vm.despertarCommit(owner, repo, authorized) }) {
                Text("Gravar atribuições (ramo novo)")
            }
        }
        item { StatusLines(vm) }
        items(inspirations) { r ->
            ItemCard(
                title = r.fullName,
                subtitle = "${r.licenseClass.label} \u00b7 ${r.spdx.ifBlank { "n/d" }} \u00b7 \u2605 ${r.stars}\n" +
                    LicensePolicy.guidance(r.licenseClass),
                onClick = r.url.takeIf { it.startsWith("https://") }?.let { u -> { uri.openUri(u) } },
            )
        }
        item { ScreenTitle("Análise reversa (Termux)") }
        item {
            Text(
                "Apenas para APKs que são SEUS ou que tem licença para analisar. Requer Termux com " +
                    "jadx/apktool instalados e allow-external-apps=true.",
            )
        }
        item {
            Button(onClick = { apkPicker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) {
                Text("Selecionar APK do dispositivo")
            }
            stagedApk?.let { selected ->
                Text("Selecionado: ${selected.displayName}")
                Text("Tamanho: ${selected.sizeBytes / 1024} KB")
                Text("SHA-256: ${selected.sha256}")
                Text("Origem copiada para: ${selected.sharedPath}")
                ScreenTitle("Inspeção estrutural local do APK")
                Text(selected.archive.asText())
            } ?: Text("Nenhum APK importado. Use o seletor para escolher um ficheiro.")
        }
        item {
            Button(onClick = { vm.despertarCheckTermux() }, modifier = Modifier.fillMaxWidth(), enabled = !busy && termuxResult?.isPending != true) {
                Text("Diagnosticar ambiente Termux")
            }
        }
        item {
            OutlinedTextField(
                value = outName, onValueChange = { outName = it },
                label = { Text("Nome da pasta de saída") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.despertarReverse("jadx", outName, authorized) }, enabled = !busy && stagedApk != null && termuxResult?.isPending != true) { Text("Analisar com JADX") }
                Button(onClick = { vm.despertarReverse("apktool", outName, authorized) }, enabled = !busy && stagedApk != null && termuxResult?.isPending != true) { Text("Analisar com APKTool") }
                OutlinedButton(onClick = { vm.despertarInspectOutput(outName, authorized) }, enabled = !busy && outName.isNotBlank() && termuxResult?.isPending != true) { Text("Inspecionar saída") }
            }
        }
        termuxResult?.let { result ->
            item {
                ScreenTitle("Resultado da execução Termux #${result.executionId}")
                Text(when {
                    result.isPending -> "Em execução ou à espera do callback do Termux…"
                    result.succeeded -> "Concluído com sucesso (exit code 0)."
                    else -> "Execução falhou ou terminou com avisos (exit code ${result.exitCode})."
                })
                if (result.internalErrorCode != -1 && !result.isPending) Text("Código interno Termux: ${result.internalErrorCode}")
                if (result.internalError.isNotBlank()) Text("Erro Termux: ${result.internalError}")
                if (result.stderr.isNotBlank()) Text("stderr:\n${result.stderr.take(5000)}")
                if (result.stdout.isNotBlank()) Text("stdout:\n${result.stdout.take(5000)}")
                if (buildArtifactPath != null) Text("APK validado: ${buildArtifactPath}")
                else Text("Saída esperada: ~/despertar/$outName")
                if (repairFindings.isNotEmpty()) {
                    ScreenTitle("Repair Engine · diagnóstico de compilação")
                    repairFindings.forEach { finding ->
                        Text("${finding.severity} · ${finding.title} (${finding.confidence}% confiança)")
                        if (finding.evidence.isNotBlank()) Text("Evidência: ${finding.evidence}")
                        Text("Próxima ação: ${finding.recommendation}")
                    }
                    OutlinedButton(
                        onClick = {
                            val report = repairFindings.joinToString("\n\n") {
                                "[${it.severity}] ${it.title}\nEvidência: ${it.evidence}\nAção: ${it.recommendation}\nConfiança: ${it.confidence}%"
                            }
                            clipboard.setText(AnnotatedString("HubOps · Repair Engine\n$report"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Copiar relatório de diagnóstico") }
                    Text("O Repair Engine apenas diagnostica e recomenda. Não modifica automaticamente os ficheiros do projeto.")
                }
                Text("O exit code 0 confirma a execução do comando; a própria tarefa também verifica se o APK foi criado e copiado.")
            }
        }
    }
}
