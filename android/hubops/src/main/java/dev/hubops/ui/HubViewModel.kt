package dev.hubops.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.hubops.core.config.SecureConfig
import dev.hubops.core.event.AppEvent
import dev.hubops.core.event.EventBus
import dev.hubops.core.model.CommandResult
import dev.hubops.core.model.CategoryDto
import dev.hubops.core.model.RepoResource
import dev.hubops.core.model.DeviceInfo
import dev.hubops.core.model.PostDto
import dev.hubops.core.model.RepoDto
import dev.hubops.core.model.Source
import dev.hubops.data.device.DeviceInfoService
import dev.hubops.despertar.AttributionLedger
import dev.hubops.despertar.ApkIntake
import dev.hubops.despertar.StagedApk
import android.net.Uri
import dev.hubops.despertar.DespertarService
import dev.hubops.despertar.DevEntitlement
import dev.hubops.despertar.Entitlement
import dev.hubops.despertar.SimilarRepo
import dev.hubops.despertar.RepositoryStudy
import dev.hubops.despertar.TermuxBridge
import dev.hubops.despertar.BuildArtifactParser
import dev.hubops.despertar.BuildRepairAdvisor
import dev.hubops.despertar.BuildRepairFinding
import dev.hubops.despertar.TermuxRunResult
import dev.hubops.despertar.TermuxResultService
import dev.hubops.despertar.TermuxJobs
import dev.hubops.despertar.project.AndroidProjectBlueprint
import dev.hubops.despertar.project.AndroidProjectGenerator
import dev.hubops.despertar.project.ProjectZipExporter
import dev.hubops.despertar.project.ProjectIntelligence
import dev.hubops.despertar.project.ProjectInsight
import dev.hubops.despertar.project.ProjectArchitect
import dev.hubops.despertar.project.ArchitectureReport
import dev.hubops.despertar.project.ComponentFusion
import dev.hubops.despertar.project.FusionReport
import dev.hubops.despertar.project.CodeSimilarityEngine
import dev.hubops.despertar.project.CodeSimilarityReport
import java.io.File
import java.time.LocalDate
import dev.hubops.data.github.GitHubService
import dev.hubops.data.terminal.ShellRunner
import dev.hubops.data.wordpress.WordPressService
import dev.hubops.glue.FeedAggregator
import dev.hubops.espelhar.EspelharEngine
import dev.hubops.espelhar.EspelharAnalysis
import dev.hubops.espelhar.EspelharPipelineReport
import dev.hubops.espelhar.EspelharRepair
import dev.hubops.espelhar.EspelharSnapshotDiff
import dev.hubops.core.integration.HubOpsOrchestrator
import dev.hubops.core.integration.EngineId
import dev.hubops.core.integration.TransferProposal
import dev.hubops.core.integration.TransferOutcome
import dev.hubops.core.integration.ProjectRecord
import dev.hubops.core.integration.ValidationEvidence
import dev.hubops.core.integration.ProfessionalWorkflowCoordinator
import dev.hubops.core.integration.ProfessionalWorkflowCase
import dev.hubops.core.integration.CrossEngineAssessment
import dev.hubops.core.repair.RepairPlan
import dev.hubops.core.repair.RepairResult
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

class HubViewModel(app: Application) : AndroidViewModel(app) {

    val config = SecureConfig(app)
    private val bus = EventBus()
    private val github = GitHubService(config)
    private val wordpress = WordPressService(config)
    private val shell = ShellRunner(app.filesDir)
    private val espelharWorkspace = File(app.filesDir, "espelhar-x").apply { mkdirs() }
    private val espelhar = EspelharEngine(espelharWorkspace)
    private val orchestrator = HubOpsOrchestrator(File(app.filesDir, "hubops-integration"))
    private val professionalWorkflow = ProfessionalWorkflowCoordinator(File(app.filesDir, "hubops-integration"))
    private val _professionalCase = MutableStateFlow(professionalWorkflow.latest())
    val professionalCase: StateFlow<ProfessionalWorkflowCase?> = _professionalCase.asStateFlow()
    private val _professionalAssessment = MutableStateFlow<CrossEngineAssessment?>(null)
    val professionalAssessment: StateFlow<CrossEngineAssessment?> = _professionalAssessment.asStateFlow()
    private var professionalSource: File? = null
    private var professionalWorkspacePrefix: String? = null
    private var professionalReplacement: File? = null
    private var professionalRepairPlan: RepairPlan? = null
    private var professionalPendingBuildReference: String? = null
    private var professionalReauditSourceReady: Boolean = false
    private val _professionalReauditReady = MutableStateFlow(false)
    val professionalReauditReady: StateFlow<Boolean> = _professionalReauditReady.asStateFlow()
    private val _integrationProposal = MutableStateFlow(orchestrator.pendingProposals().firstOrNull())
    val integrationProposal: StateFlow<TransferProposal?> = _integrationProposal.asStateFlow()
    private val _integrationOutcome = MutableStateFlow<TransferOutcome?>(null)
    val integrationOutcome: StateFlow<TransferOutcome?> = _integrationOutcome.asStateFlow()
    private val _integrationAudit = MutableStateFlow(orchestrator.recentAuditLines())
    val integrationAudit: StateFlow<List<String>> = _integrationAudit.asStateFlow()
    private val _registeredProjects = MutableStateFlow(orchestrator.recentProjectRecords())
    val registeredProjects: StateFlow<List<ProjectRecord>> = _registeredProjects.asStateFlow()
    private val _espelharAnalysis = MutableStateFlow<EspelharAnalysis?>(null)
    val espelharAnalysis: StateFlow<EspelharAnalysis?> = _espelharAnalysis.asStateFlow()
    private val _espelharPipeline = MutableStateFlow<EspelharPipelineReport?>(null)
    val espelharPipeline: StateFlow<EspelharPipelineReport?> = _espelharPipeline.asStateFlow()
    private val _espelharSnapshotDiff = MutableStateFlow<EspelharSnapshotDiff?>(null)
    val espelharSnapshotDiff: StateFlow<EspelharSnapshotDiff?> = _espelharSnapshotDiff.asStateFlow()
    private val _espelharAppliedRepairs = MutableStateFlow<List<EspelharRepair>>(emptyList())
    val espelharAppliedRepairs: StateFlow<List<EspelharRepair>> = _espelharAppliedRepairs.asStateFlow()
    private val _espelharOutput = MutableStateFlow<CommandResult?>(null)
    private val _espelharLoaded = MutableStateFlow<Pair<String, String>?>(null)
    val espelharLoaded: StateFlow<Pair<String, String>?> = _espelharLoaded.asStateFlow()
    val espelharOutput: StateFlow<CommandResult?> = _espelharOutput.asStateFlow()
    val terminalWorkingDirectory: String = shell.workingDirectoryPath
    private val device = DeviceInfoService(app)
    private val despertar = DespertarService(config)
    private val termux = TermuxBridge(app)
    private val apkIntake = ApkIntake(app)
    private val _stagedApk = MutableStateFlow<StagedApk?>(null)
    val stagedApk: StateFlow<StagedApk?> = _stagedApk.asStateFlow()
    private val _termuxResult = MutableStateFlow<TermuxRunResult?>(null)
    private val _generatedProject = MutableStateFlow<File?>(null)
    val generatedProject: StateFlow<File?> = _generatedProject.asStateFlow()
    private val _buildArtifactPath = MutableStateFlow<String?>(null)
    val buildArtifactPath: StateFlow<String?> = _buildArtifactPath.asStateFlow()
    private val _buildRepairFindings = MutableStateFlow<List<BuildRepairFinding>>(emptyList())
    val buildRepairFindings: StateFlow<List<BuildRepairFinding>> = _buildRepairFindings.asStateFlow()
    private val _projectInsight = MutableStateFlow<ProjectInsight?>(null)
    val projectInsight: StateFlow<ProjectInsight?> = _projectInsight.asStateFlow()
    private val _architectureReport = MutableStateFlow<ArchitectureReport?>(null)
    val architectureReport: StateFlow<ArchitectureReport?> = _architectureReport.asStateFlow()
    private val _fusionSource = MutableStateFlow<File?>(null)
    val fusionSource: StateFlow<File?> = _fusionSource.asStateFlow()
    private val _fusionReport = MutableStateFlow<FusionReport?>(null)
    val fusionReport: StateFlow<FusionReport?> = _fusionReport.asStateFlow()
    private val _codeSimilarityReport = MutableStateFlow<CodeSimilarityReport?>(null)
    val codeSimilarityReport: StateFlow<CodeSimilarityReport?> = _codeSimilarityReport.asStateFlow()
    val termuxResult: StateFlow<TermuxRunResult?> = _termuxResult.asStateFlow()
    val entitlement: Entitlement = DevEntitlement(config)

    private val _inspirations = MutableStateFlow<List<SimilarRepo>>(emptyList())
    val inspirations: StateFlow<List<SimilarRepo>> = _inspirations.asStateFlow()

    private val _repositoryStudy = MutableStateFlow<RepositoryStudy?>(null)
    val repositoryStudy: StateFlow<RepositoryStudy?> = _repositoryStudy.asStateFlow()

    val aggregator = FeedAggregator(bus, viewModelScope)

    private val _repos = MutableStateFlow<List<RepoDto>>(emptyList())
    val repos: StateFlow<List<RepoDto>> = _repos.asStateFlow()

    private val _posts = MutableStateFlow<List<PostDto>>(emptyList())
    val posts: StateFlow<List<PostDto>> = _posts.asStateFlow()

    private val _pages = MutableStateFlow<List<PostDto>>(emptyList())
    val pages: StateFlow<List<PostDto>> = _pages.asStateFlow()

    private val _categories = MutableStateFlow<List<CategoryDto>>(emptyList())
    val categories: StateFlow<List<CategoryDto>> = _categories.asStateFlow()

    private val _repoResources = MutableStateFlow<List<RepoResource>>(emptyList())
    val repoResources: StateFlow<List<RepoResource>> = _repoResources.asStateFlow()

    private val _selectedRepo = MutableStateFlow<String?>(null)
    val selectedRepo: StateFlow<String?> = _selectedRepo.asStateFlow()

    private val _history = MutableStateFlow<List<CommandResult>>(emptyList())
    val history: StateFlow<List<CommandResult>> = _history.asStateFlow()

    private val _deviceInfo = MutableStateFlow<DeviceInfo?>(null)
    val deviceInfo: StateFlow<DeviceInfo?> = _deviceInfo.asStateFlow()

    private val operationMutex = Mutex()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    init {
        refreshDevice()
        refreshTermuxResult()
    }

    private fun launchOp(source: Source, block: suspend () -> Unit) {
        viewModelScope.launch {
            if (!operationMutex.tryLock()) {
                _notice.value = "Já existe uma operação em curso. Aguarde a sua conclusão."
                return@launch
            }
            _busy.value = true
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                bus.publish(AppEvent.Failure(source, e.message ?: e.javaClass.simpleName))
            } finally {
                _busy.value = false
                operationMutex.unlock()
            }
        }
    }

    /** Exporta snapshot do workspace Espelhar-X; diagnóstico e reparação passam a referir o mesmo projeto. */
    fun beginProfessionalWorkflow(workspacePrefix: String, buildReference: String, reAudit: Boolean = false) = launchOp(Source.DESPERTAR) {
        val clean = workspacePrefix.trim()
        require(clean.isNotBlank() && !clean.startsWith('/') && !clean.contains('\\') &&
            clean.split('/').all { it.isNotBlank() && it != "." && it != ".." }) { "Prefixo do workspace inválido." }
        val archive = File(getApplication<Application>().cacheDir, "hubops-workflow-${System.currentTimeMillis()}.zip")
        withContext(Dispatchers.IO) { espelhar.exportProjectZip(clean, archive) }
        require(archive.isFile && archive.length() > 0L) { "Não foi possível exportar o snapshot do workspace." }
        val insight = withContext(Dispatchers.Default) { ProjectIntelligence.analyzeZip(archive) }
        val digest = withContext(Dispatchers.IO) { sourceContentSha256(archive) }
        if (reAudit) {
            val current = requireNotNull(_professionalCase.value) { "Não existe caso para nova auditoria." }
            require(current.phase == dev.hubops.core.integration.WorkflowPhase.REPAIR_APPLIED) { "A nova origem só pode ser anexada após a aplicação de uma correção." }
            require(clean == current.workspacePrefix) { "A reauditoria deve usar o mesmo projeto do caso original." }
            require(buildReference.isNotBlank() && buildReference != current.buildReference) { "Indique uma referência distinta para a nova build." }
            require(!digest.equals(current.sourceSha256, ignoreCase = true)) { "O snapshot atualizado tem o mesmo SHA-256 da origem anterior; nenhuma alteração de fonte foi detetada." }
            professionalSource?.delete(); professionalSource = archive
            professionalWorkspacePrefix = clean
            professionalPendingBuildReference = buildReference.trim()
            professionalReauditSourceReady = true
            _professionalReauditReady.value = true
            _professionalAssessment.value = null
            _notice.value = "Novo snapshot analisado: ${insight.filesScanned} ficheiros. Selecione agora o APK reconstruído."
        } else {
            professionalSource?.delete(); professionalSource = archive
            professionalWorkspacePrefix = clean
            _professionalCase.value = professionalWorkflow.begin(buildReference, archive.name, digest, clean)
            _professionalAssessment.value = null
            professionalReauditSourceReady = false
            professionalPendingBuildReference = null
            _professionalReauditReady.value = false
            _notice.value = "Caso Pro iniciado a partir do workspace $clean: ${insight.filesScanned} ficheiros; AST ${insight.astReport.filesParsed}/${insight.astReport.filesConsidered}."
        }
        _projectInsight.value = insight
    }

    /** Retoma apenas se o snapshot atual do workspace corresponder ao hash persistido do caso. */
    fun resumeProfessionalWorkflow(workspacePrefix: String) = launchOp(Source.DESPERTAR) {
        val current = requireNotNull(_professionalCase.value) { "Não existe caso persistido para retomar." }
        val clean = workspacePrefix.trim()
        require(clean == current.workspacePrefix) { "O prefixo não corresponde ao workspace registado no caso." }
        require(current.phase !in setOf(dev.hubops.core.integration.WorkflowPhase.REPAIR_APPLIED, dev.hubops.core.integration.WorkflowPhase.REAUDIT_COMPLETE)) {
            "Este caso não pode ser retomado nesta fase. Após reparação, anexe novo snapshot/build; casos concluídos permanecem fechados."
        }
        val archive = File(getApplication<Application>().cacheDir, "hubops-resume-${System.currentTimeMillis()}.zip")
        withContext(Dispatchers.IO) { espelhar.exportProjectZip(clean, archive) }
        require(archive.isFile && archive.length() > 0L) { "Não foi possível exportar o snapshot do workspace." }
        val digest = withContext(Dispatchers.IO) { sourceContentSha256(archive) }
        require(digest.equals(current.sourceSha256, ignoreCase = true)) {
            "O workspace mudou desde o último registo. Não é possível retomar este estado; crie uma nova avaliação do projeto."
        }
        val insight = withContext(Dispatchers.Default) { ProjectIntelligence.analyzeZip(archive) }
        professionalSource?.delete(); professionalSource = archive
        professionalWorkspacePrefix = clean
        professionalReplacement = null; professionalRepairPlan = null
        professionalPendingBuildReference = null
        _projectInsight.value = insight
        _professionalAssessment.value = null
        if (current.phase == dev.hubops.core.integration.WorkflowPhase.REPAIR_PREPARED) {
            _professionalCase.value = professionalWorkflow.invalidatePreparedRepair(current.id,
                "Sessão retomada sem o ficheiro de correção original; exige nova preparação e aprovação.")
            _notice.value = "Caso retomado. Plano anterior invalidado por segurança; prepare novamente a correção."
        } else {
            _notice.value = "Caso ${current.id} retomado; snapshot do workspace verificado por SHA-256."
        }
    }

    fun assessProfessionalWorkflow(contract: com.hubops.vislumbrops.core.VerificationContract) = launchOp(Source.DESPERTAR) {
        val current = requireNotNull(_professionalCase.value) { "Inicie primeiro um caso Pro com um ZIP de origem." }
        val source = requireNotNull(professionalSource) { "A origem do caso não está disponível nesta sessão; selecione novamente o ZIP." }
        val assessment = withContext(Dispatchers.Default) {
            orchestrator.assessProjectAcrossEngines(source, current.buildReference, contract)
        }
        val case = professionalWorkflow.recordAssessment(current.id, assessment.evidenceSha256,
            contract.verificationId, contract.status, assessment.requiresReview, contract.artifactHash)
        _professionalAssessment.value = assessment
        _professionalCase.value = case
        _notice.value = "Avaliação cruzada concluída: ${assessment.findings.size} achados; ${assessment.checks.size} verificações."
    }

    fun prepareProfessionalRepair(context: android.content.Context, relativeTarget: String, replacementUri: Uri) = launchOp(Source.TERMINAL) {
        val current = requireNotNull(_professionalCase.value) { "Não existe caso Pro ativo." }
        require(current.phase in setOf(dev.hubops.core.integration.WorkflowPhase.ASSESSED, dev.hubops.core.integration.WorkflowPhase.NEEDS_REVIEW)) {
            "A correção só pode ser preparada após a avaliação cruzada."
        }
        require(_professionalAssessment.value != null) { "Execute novamente a avaliação cruzada do caso antes de preparar uma correção." }
        val prefix = requireNotNull(professionalWorkspacePrefix) { "Workspace do caso não está associado nesta sessão; retome o caso primeiro." }
        require(relativeTarget.startsWith("$prefix/")) { "O destino deve permanecer dentro do projeto $prefix." }
        require(!relativeTarget.contains('\\') && relativeTarget.split('/').none { it.isBlank() || it == "." || it == ".." }) { "Caminho relativo inválido." }
        val prefixRoot = File(espelharWorkspace, prefix).canonicalFile
        val targetFile = File(espelharWorkspace, relativeTarget)
        val normalizedTarget = targetFile.toPath().toAbsolutePath().normalize()
        require(normalizedTarget.startsWith(prefixRoot.toPath().toAbsolutePath().normalize())) { "Destino fora do projeto associado." }
        require(!java.nio.file.Files.isSymbolicLink(targetFile.toPath()) && targetFile.isFile) { "O ficheiro-alvo deve existir e não pode ser link simbólico." }
        require(targetFile.canonicalPath.startsWith(prefixRoot.path + File.separator)) { "Destino resolvido fora do projeto associado." }
        val replacement = File(context.cacheDir, "hubops-replacement-${System.currentTimeMillis()}.bin")
        withContext(Dispatchers.IO) {
            val input = requireNotNull(context.contentResolver.openInputStream(replacementUri)) { "Não foi possível abrir o ficheiro de correção." }
            input.use { src -> replacement.outputStream().use { dst ->
                val buffer = ByteArray(16 * 1024); var total = 0L
                while (true) { val n = src.read(buffer); if (n < 0) break; total += n
                    require(total <= 5L * 1024 * 1024) { "Correção excede 5 MB." }; dst.write(buffer, 0, n) }
            } }
            require(replacement.length() > 0L) { "Ficheiro de correção vazio." }
        }
        val plan = withContext(Dispatchers.IO) { orchestrator.prepareControlledRepair(espelharWorkspace, relativeTarget, replacement) }
        professionalReplacement?.delete(); professionalReplacement = replacement
        professionalRepairPlan = plan
        _professionalCase.value = professionalWorkflow.recordRepairPrepared(current.id, plan)
        _notice.value = "Plano ${plan.id} preparado. Destino não foi alterado; aprovação explícita pendente."
    }

    /** Handoff a reviewed SNSE proposal into the existing Pro Flow approval gate. */
    fun prepareProfessionalRepairFromPatch(relativeTarget: String, originalCode: String, patchContent: String) = launchOp(Source.TERMINAL) {
        val current = requireNotNull(_professionalCase.value) { "Não existe caso Pro ativo." }
        require(current.phase in setOf(dev.hubops.core.integration.WorkflowPhase.ASSESSED, dev.hubops.core.integration.WorkflowPhase.NEEDS_REVIEW)) {
            "A proposta SNSE só pode ser anexada após a avaliação cruzada."
        }
        require(_professionalAssessment.value != null) { "Execute primeiro a avaliação cruzada no Pro Flow." }
        require(originalCode.isNotBlank() && originalCode.length <= 1_000_000) { "Código original vazio ou superior a 1 MB." }
        require(patchContent.isNotBlank() && patchContent.length <= 5 * 1024 * 1024) { "Patch vazio ou superior a 5 MB." }
        val prefix = requireNotNull(professionalWorkspacePrefix) { "Workspace do caso não está associado nesta sessão; retome o caso." }
        require(relativeTarget.startsWith("$prefix/") && !relativeTarget.contains('\\') &&
            relativeTarget.split('/').none { it.isBlank() || it == "." || it == ".." }) { "Destino fora do projeto associado ou caminho inválido." }
        val prefixRoot = File(espelharWorkspace, prefix).canonicalFile
        val target = File(espelharWorkspace, relativeTarget)
        require(!java.nio.file.Files.isSymbolicLink(target.toPath()) && target.isFile && target.length() in 1..1_048_576L) {
            "O ficheiro-alvo deve existir, não pode ser link simbólico e não pode exceder 1 MB."
        }
        require(target.canonicalPath.startsWith(prefixRoot.path + File.separator)) { "Destino resolvido fora do projeto associado." }
        val actual = withContext(Dispatchers.IO) { target.readText(Charsets.UTF_8) }
        require(actual == originalCode) { "O código analisado pelo SNSE não corresponde exatamente ao ficheiro atual. Recarregue o conteúdo e repita a análise." }
        val replacement = File(getApplication<Application>().cacheDir, "hubops-snse-${System.nanoTime()}.patch")
        withContext(Dispatchers.IO) { replacement.writeText(patchContent, Charsets.UTF_8) }
        val plan = try {
            withContext(Dispatchers.IO) { orchestrator.prepareControlledRepair(espelharWorkspace, relativeTarget, replacement) }
        } catch (failure: Exception) {
            replacement.delete()
            throw failure
        }
        professionalReplacement?.delete(); professionalReplacement = replacement
        professionalRepairPlan = plan
        _professionalCase.value = professionalWorkflow.recordRepairPrepared(current.id, plan)
        _notice.value = "Proposta SNSE anexada ao Pro Flow. Nenhuma alteração foi aplicada; reveja o hash e aprove no painel Pro Flow."
    }

    fun approveProfessionalRepair() = launchOp(Source.TERMINAL) {
        val current = requireNotNull(_professionalCase.value) { "Não existe caso Pro ativo." }
        val plan = requireNotNull(professionalRepairPlan) { "Plano não está disponível nesta sessão; prepare novamente após rever o destino." }
        val replacement = requireNotNull(professionalReplacement) { "Ficheiro de correção não está disponível; prepare novamente." }
        require(current.repairPlanId == plan.id && current.phase == dev.hubops.core.integration.WorkflowPhase.REPAIR_PREPARED) { "Plano já não corresponde ao caso ativo." }
        val result = withContext(Dispatchers.IO) { orchestrator.approveAndApplyControlledRepair(espelharWorkspace, plan, plan.approvalToken, replacement) }
        _professionalCase.value = professionalWorkflow.recordRepairApplied(current.id, result)
        professionalReauditSourceReady = false
        _professionalReauditReady.value = false
        _notice.value = "Correção aplicada com backup. Reconstrua o projeto e selecione o novo ZIP e APK para nova auditoria."
    }

    fun rollbackProfessionalRepair() = launchOp(Source.TERMINAL) {
        val current = requireNotNull(_professionalCase.value) { "Não existe caso Pro ativo." }
        require(current.phase == dev.hubops.core.integration.WorkflowPhase.REPAIR_APPLIED) { "Não existe reparação ativa para reverter." }
        val planId = requireNotNull(current.repairPlanId) { "O caso não identifica o plano aplicado." }
        val result = withContext(Dispatchers.IO) { orchestrator.rollbackControlledRepair(espelharWorkspace, planId) }
        _professionalCase.value = professionalWorkflow.recordRepairRolledBack(current.id, result)
        professionalReauditSourceReady = false
        professionalPendingBuildReference = null
        _professionalReauditReady.value = false
        professionalSource?.delete(); professionalSource = null
        _professionalAssessment.value = null
        _notice.value = "Correção revertida com backup verificado. Reanalise o workspace e reconstrua o APK antes de continuar."
    }

    fun recordProfessionalReaudit(contract: com.hubops.vislumbrops.core.VerificationContract) = launchOp(Source.DESPERTAR) {
        val current = requireNotNull(_professionalCase.value) { "Não existe caso Pro ativo." }
        require(current.phase == dev.hubops.core.integration.WorkflowPhase.REPAIR_APPLIED) { "A nova auditoria exige reparação aplicada." }
        require(professionalReauditSourceReady) { "Selecione e analise o ZIP atualizado da nova build antes de registar a reauditoria." }
        val source = requireNotNull(professionalSource) { "Selecione o snapshot atualizado após reconstruir o projeto." }
        require(professionalWorkspacePrefix == current.workspacePrefix) { "O snapshot pertence a outro workspace." }
        val newBuildReference = requireNotNull(professionalPendingBuildReference) { "Indique uma nova referência de build." }
        val assessment = withContext(Dispatchers.Default) { orchestrator.assessProjectAcrossEngines(source, newBuildReference, contract) }
        _professionalCase.value = professionalWorkflow.recordReaudit(current.id, contract.verificationId, contract.status,
            assessment.evidenceSha256, assessment.requiresReview, source.name, sourceContentSha256(source), newBuildReference, contract.artifactHash)
        professionalReauditSourceReady = false
        professionalPendingBuildReference = null
        _professionalReauditReady.value = false
        _professionalAssessment.value = assessment
        _notice.value = _professionalCase.value?.lastMessage
    }

    /** Fingerprint determinístico de todos os caminhos/conteúdos exportados; ignora timestamps/ordem ZIP. */
    private fun sourceContentSha256(archive: File): String {
        val entries = linkedMapOf<String, ByteArray>()
        var totalBytes = 0L
        java.util.zip.ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                require(entries.size < 200) { "Snapshot excede 200 ficheiros." }
                val path = entry.name
                require(path.isNotBlank() && !path.startsWith('/') && !path.contains('\\') &&
                    path.split('/').none { it.isBlank() || it == "." || it == ".." }) { "Caminho inseguro no snapshot." }
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                var entryBytes = 0L
                while (true) {
                    val count = zip.read(buffer)
                    if (count < 0) break
                    entryBytes += count; totalBytes += count
                    require(entryBytes <= 500_000 && totalBytes <= 10L * 1024 * 1024) { "Snapshot excede os limites de conteúdo." }
                    output.write(buffer, 0, count)
                }
                require(entries.put(path, output.toByteArray()) == null) { "Snapshot contém caminhos duplicados." }
                zip.closeEntry()
            }
        }
        require(entries.isNotEmpty()) { "Snapshot sem ficheiros." }
        val digest = MessageDigest.getInstance("SHA-256")
        entries.toSortedMap().forEach { (path, bytes) ->
            digest.update(path.toByteArray(Charsets.UTF_8)); digest.update(0.toByte())
            digest.update(bytes); digest.update(0.toByte())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun searchRepos(query: String, page: Int = 1) = launchOp(Source.GITHUB) {
        val items = github.searchRepos(query, page)
        _repos.value = items
        bus.publish(AppEvent.ReposLoaded(items))
    }

    fun userRepos(user: String, page: Int = 1) = launchOp(Source.GITHUB) {
        val items = github.userRepos(user, page)
        _repos.value = items
        bus.publish(AppEvent.ReposLoaded(items))
    }

    fun myRepos(page: Int = 1) = launchOp(Source.GITHUB) {
        val items = github.myRepos(page)
        _repos.value = items
        bus.publish(AppEvent.ReposLoaded(items))
    }

    fun loadRepoResources(fullName: String, kind: String, page: Int = 1) = launchOp(Source.GITHUB) {
        _selectedRepo.value = fullName
        _repoResources.value = github.repoResources(fullName, kind, page)
        _notice.value = "${_repoResources.value.size} elementos de $kind carregados para $fullName."
    }

    fun loadPosts(site: String, page: Int = 1) = launchOp(Source.WORDPRESS) {
        val normalized = WordPressService.normalizeSite(site)
        config.wpSite = normalized
        val items = wordpress.latestPosts(normalized, page)
        _posts.value = items
        bus.publish(AppEvent.PostsLoaded(items))
    }

    fun loadPages(site: String, page: Int = 1) = launchOp(Source.WORDPRESS) {
        val normalized = WordPressService.normalizeSite(site)
        config.wpSite = normalized
        val items = wordpress.latestPages(normalized, page)
        _pages.value = items
        bus.publish(AppEvent.PagesLoaded(items))
        _notice.value = "${items.size} páginas carregadas."
    }

    fun loadCategories(site: String, page: Int = 1) = launchOp(Source.WORDPRESS) {
        val normalized = WordPressService.normalizeSite(site)
        config.wpSite = normalized
        _categories.value = wordpress.categories(normalized, page)
        _notice.value = "${_categories.value.size} categorias carregadas."
    }

    fun createDraft(site: String, title: String, content: String) = launchOp(Source.WORDPRESS) {
        val normalized = WordPressService.normalizeSite(site)
        config.wpSite = normalized
        val link = wordpress.createDraft(normalized, title, content)
        _notice.value = if (link.isBlank()) "Rascunho criado." else "Rascunho criado: $link"
    }

    fun runCommand(command: String) = launchOp(Source.TERMINAL) {
        val result = withContext(Dispatchers.IO) { shell.run(command) }
        _history.value = (_history.value + result).takeLast(20)
        bus.publish(AppEvent.CommandFinished(result))
    }

    fun clearFeed() {
        aggregator.clear()
        _notice.value = "Feed e registo de atividade limpos."
    }

    fun clearTerminalHistory() {
        _history.value = emptyList()
        _notice.value = "Histórico desta sessão limpo."
    }

    fun refreshDevice() {
        viewModelScope.launch {
            try {
                _deviceInfo.value = withContext(Dispatchers.IO) { device.read() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _notice.value = "Não foi possível atualizar o diagnóstico do dispositivo: ${e.message ?: "erro desconhecido"}"
            }
        }
    }

    fun analyzeEspelhar(path: String, source: String) = launchOp(Source.TERMINAL) {
        _espelharAnalysis.value = withContext(Dispatchers.Default) { espelhar.analyze(path, source) }
        _notice.value = "Espelhar-X analisou $path localmente; nenhuma execução foi iniciada."
    }

    fun saveEspelharFile(path: String, source: String) = launchOp(Source.TERMINAL) {
        val file = withContext(Dispatchers.IO) { espelhar.save(path, source) }
        _notice.value = "Ficheiro guardado no workspace privado: ${file.relativeTo(espelharWorkspace).path}"
    }

    fun loadEspelharFile(path: String) = launchOp(Source.TERMINAL) {
        val source = withContext(Dispatchers.IO) { espelhar.read(path) }
        _espelharLoaded.value = path to source
        _notice.value = "Ficheiro carregado: $path"
    }

    fun runEspelharShell(source: String) = launchOp(Source.TERMINAL) {
        require(source.length <= 100_000) { "Script demasiado grande (máx. 100 KB)." }
        val script = File(espelharWorkspace, "run-${System.currentTimeMillis()}.sh")
        withContext(Dispatchers.IO) { script.writeText(source, Charsets.UTF_8) }
        try {
            _espelharOutput.value = withContext(Dispatchers.IO) {
                shell.run("cd '${espelharWorkspace.absolutePath}' && sh '${script.name}'")
            }
            _notice.value = "Execução concluída no UID privado da aplicação. Isto não é isolamento proot/container."
        } finally { script.delete() }
    }

    fun applyEspelharRepair(repair: EspelharRepair) = launchOp(Source.TERMINAL) {
        val backup = withContext(Dispatchers.IO) { dev.hubops.espelhar.EspelharProjectServices.applyRepair(espelharWorkspace, repair) }
        _espelharAppliedRepairs.value = (_espelharAppliedRepairs.value.filterNot { it.path == repair.path } + repair)
        _espelharPipeline.value = withContext(Dispatchers.IO) { espelhar.pipeline() }
        _notice.value = "Reparação aplicada com verificação de integridade. Cópia original: ${backup.name}. Pode reverter enquanto o ficheiro não for alterado novamente."
    }

    fun rollbackEspelharRepair(repair: EspelharRepair) = launchOp(Source.TERMINAL) {
        val archived = withContext(Dispatchers.IO) { dev.hubops.espelhar.EspelharProjectServices.rollbackRepair(espelharWorkspace, repair) }
        _espelharAppliedRepairs.value = _espelharAppliedRepairs.value.filterNot { it.path == repair.path }
        _espelharPipeline.value = withContext(Dispatchers.IO) { espelhar.pipeline() }
        _notice.value = "Reparação revertida e conteúdo original verificado. Backup de auditoria: ${archived.name}."
    }

    fun importGitHubRepositoryToEspelhar(fullName: String, ref: String = "") = launchOp(Source.GITHUB) {
        val snapshot = github.sourceSnapshot(fullName, ref)
        val prefix = "github-${fullName.substringBefore('/').lowercase()}-${fullName.substringAfter('/').lowercase()}"
        val imported = withContext(Dispatchers.IO) { espelhar.importSources(snapshot, prefix) }
        _notice.value = "$imported ficheiro(s) importados em modo somente-leitura para análise local ($fullName). Nenhum push foi realizado."
    }

    fun compareGitHubRepositoryWithWorkspace(fullName: String, ref: String = "") = launchOp(Source.GITHUB) {
        val snapshot = github.sourceSnapshot(fullName, ref)
        val prefix = "github-${fullName.substringBefore('/').lowercase()}-${fullName.substringAfter('/').lowercase()}"
        _espelharSnapshotDiff.value = withContext(Dispatchers.IO) {
            dev.hubops.espelhar.EspelharProjectServices.compareSnapshotToWorkspace(espelharWorkspace, snapshot, prefix)
        }
        val diff = _espelharSnapshotDiff.value
        _notice.value = "Comparação GitHub concluída: ${diff?.added?.size ?: 0} novos, ${diff?.changed?.size ?: 0} alterados, ${diff?.unchanged?.size ?: 0} iguais e ${diff?.localOnly?.size ?: 0} apenas locais. Nenhum ficheiro foi alterado."
    }

    fun runEspelharPipeline() = launchOp(Source.TERMINAL) {
        _espelharPipeline.value = withContext(Dispatchers.IO) { espelhar.pipeline() }
        _notice.value = "Pipeline local concluído: ${_espelharPipeline.value?.filesChecked ?: 0} ficheiros verificados."
    }

    /** Fluxo inverso, somente-leitura: o Despertar recebe um snapshot do Espelhar-X para análise partilhada. */
    fun inspectEspelharProjectInDespertar(prefix: String) = launchOp(Source.DESPERTAR) {
        val clean = prefix.trim()
        require(clean.isNotBlank()) { "Indique a pasta do projeto no workspace Espelhar-X." }
        val archive = File(getApplication<Application>().cacheDir, "espelhar-readonly-${System.currentTimeMillis()}.zip")
        try {
            val insight = withContext(Dispatchers.IO) {
                espelhar.exportProjectZip(clean, archive)
                val result = ProjectIntelligence.analyzeZip(archive)
                fun hash(file: File): String {
                    val digest = MessageDigest.getInstance("SHA-256")
                    file.inputStream().buffered().use { input ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
                    }
                    return digest.digest().joinToString("") { "%02x".format(it) }
                }
                val reportHash = MessageDigest.getInstance("SHA-256").digest(result.toMarkdown().toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
                orchestrator.recordReadOnlyInspection(
                    EngineId.ESPELHAR_X, EngineId.DESPERTAR, clean, hash(archive), reportHash, result.filesScanned,
                )
                result
            }
            _projectInsight.value = insight
            _integrationAudit.value = orchestrator.recentAuditLines()
            _notice.value = "Despertar analisou o snapshot do Espelhar-X em modo somente-leitura: ${insight.filesScanned} ficheiros. Nenhuma alteração foi aplicada."
        } finally { archive.delete() }
    }

    /** Fase 1: regista origem, hash, impacto e colisões; não altera o workspace. */
    fun prepareGeneratedProjectTransfer() = launchOp(Source.DESPERTAR) {
        val source = requireNotNull(_generatedProject.value) { "Gere primeiro um projeto no Despertar." }
        val target = "projects/despertar/${System.nanoTime()}"
        val proposal = withContext(Dispatchers.IO) {
            orchestrator.prepareZipTransfer(source, EngineId.DESPERTAR, EngineId.ESPELHAR_X, target, espelharWorkspace)
        }
        _integrationProposal.value = proposal
        _integrationOutcome.value = null
        _integrationAudit.value = orchestrator.recentAuditLines()
        _registeredProjects.value = orchestrator.recentProjectRecords()
        _notice.value = "Impacto calculado. Reveja origem, destino, hash, ficheiros e colisões antes de autorizar a transferência."
    }

    /** Fase 2: importa em staging, valida, e só depois promove para o workspace definitivo. */
    fun canApproveGeneratedProjectTransfer(proposalId: String): Boolean = orchestrator.artifactFile(proposalId)?.isFile == true

    fun cancelIntegrationProposal(proposalId: String) = launchOp(Source.DESPERTAR) {
        val proposal = _integrationProposal.value?.takeIf { it.id == proposalId }
        if (proposal?.status?.name in setOf("IMPORTED_TO_STAGING", "VALIDATION_BLOCKED", "FAILED")) {
            withContext(Dispatchers.IO) { espelhar.discardIntegrationStaging("integration-staging/$proposalId") }
        }
        val cancelled = withContext(Dispatchers.IO) { orchestrator.cancelProposal(proposalId) }
        if (cancelled) {
            _integrationProposal.value = null
            _integrationOutcome.value = null
            _integrationAudit.value = orchestrator.recentAuditLines()
            _notice.value = "Proposta de integração cancelada. Nenhuma alteração foi promovida."
        }
    }

    fun approveGeneratedProjectTransfer() = launchOp(Source.DESPERTAR) {
        val proposal = requireNotNull(_integrationProposal.value) { "Prepare primeiro uma proposta de integração." }
        val archive = requireNotNull(orchestrator.artifactFile(proposal.id)) { "O artefacto privado da proposta já não está disponível." }
        val stage = "integration-staging/${proposal.id}"
        val outcome = withContext(Dispatchers.IO) {
            orchestrator.executeTransfer(
                proposalId = proposal.id,
                archive = archive,
                stagingPrefix = stage,
                importToStaging = { zip, prefix -> espelhar.importZip(zip, prefix) },
                validateStaging = { prefix ->
                    val report = espelhar.pipeline(prefix)
                    val astExpected = File(espelharWorkspace, prefix).walkTopDown().filter { it.isFile && it.extension.lowercase() in setOf("kt", "kts", "java", "py") }.count()
                    val ast = report.ast
                    val limitations = buildList {
                        if (ast == null) add("Relatório AST indisponível.")
                        ast?.unavailableReasons?.distinct()?.forEach { add(it) }
                        if ((ast?.truncatedFiles ?: 0) > 0) add("AST limitada por quantidade de nós.")
                    }
                    val evidenceDigest = MessageDigest.getInstance("SHA-256")
                    File(espelharWorkspace, prefix).walkTopDown().filter { it.isFile }.sortedBy { it.relativeTo(File(espelharWorkspace, prefix)).invariantSeparatorsPath }.forEach { file ->
                        evidenceDigest.update(file.relativeTo(File(espelharWorkspace, prefix)).invariantSeparatorsPath.toByteArray(Charsets.UTF_8))
                        file.inputStream().buffered().use { input ->
                            val buffer = ByteArray(8 * 1024)
                            while (true) { val count = input.read(buffer); if (count < 0) break; evidenceDigest.update(buffer, 0, count) }
                        }
                    }
                    val evidenceText = "${report.filesChecked}|${report.errors}|${report.warnings}|${ast?.filesParsed}|${ast?.filesUnavailable}|${ast?.filesWithSyntaxErrors}|${report.tasks.joinToString { it.name + ":" + it.status }}"
                    evidenceDigest.update(evidenceText.toByteArray(Charsets.UTF_8))
                    val digest = evidenceDigest.digest().joinToString("") { "%02x".format(it) }
                    ValidationEvidence(
                        filesChecked = report.filesChecked,
                        expectedImportedFiles = proposal.impact.supportedFiles,
                        errors = report.errors + (ast?.filesWithSyntaxErrors ?: 0),
                        warnings = report.warnings,
                        astExpectedFiles = astExpected,
                        astParsedFiles = ast?.filesParsed ?: 0,
                        astUnavailableFiles = ast?.filesUnavailable ?: if (astExpected > 0) astExpected else 0,
                        astSyntaxErrorFiles = ast?.filesWithSyntaxErrors ?: 0,
                        astTruncatedFiles = ast?.truncatedFiles ?: 0,
                        filesOmitted = ast?.filesOmitted ?: 0,
                        evidenceSha256 = digest,
                        limitations = limitations,
                    )
                },
                promoteStaging = { from, to -> espelhar.promoteStagedProject(from, to) },
            )
        }
        _integrationOutcome.value = outcome
        _integrationProposal.value = outcome.proposal
        _integrationAudit.value = orchestrator.recentAuditLines()
        _registeredProjects.value = orchestrator.recentProjectRecords()
        _espelharPipeline.value = withContext(Dispatchers.IO) { espelhar.pipeline() }
        _notice.value = outcome.message
    }

    fun saveSettings(token: String, site: String, user: String, appPassword: String) {
        if (token.length > 500 || user.length > 200 || appPassword.length > 500) {
            _notice.value = "Configuração rejeitada: um dos campos excede o limite permitido."
            return
        }
        if (token.any { it.isWhitespace() || it.isISOControl() } || user.contains(':') ||
            user.any { it.isISOControl() } || appPassword.any { it.isISOControl() }) {
            _notice.value = "Configuração rejeitada: credenciais contêm caracteres de controlo ou formato inválido."
            return
        }
        val normalizedSite = if (site.isBlank()) "" else try {
            WordPressService.normalizeSite(site)
        } catch (e: IllegalArgumentException) {
            _notice.value = e.message ?: "Endereço WordPress inválido."
            return
        }
        if (user.isBlank() != appPassword.isBlank()) {
            _notice.value = "Configure o utilizador WordPress e a Application Password em conjunto."
            return
        }
        config.githubToken = token
        config.wpSite = normalizedSite
        config.wpUser = user
        config.wpAppPassword = appPassword
        _notice.value = "Configuração guardada de forma cifrada neste dispositivo."
    }

    private fun requireDespertar(authorized: Boolean) {
        require(entitlement.isUnlocked()) { "O Despertar é um recurso pago e está bloqueado." }
        require(authorized) { "Confirme que tem autorização sobre o repositório ou APK selecionado." }
    }

    fun despertarCheck(owner: String, repo: String, authorized: Boolean) = launchOp(Source.DESPERTAR) {
        requireDespertar(authorized)
        val a = despertar.checkAccess(owner, repo)
        _notice.value = if (a.canPush) "Acesso de escrita confirmado em ${a.fullName}."
        else "Sem permissão de escrita em ${a.fullName}: só leitura."
    }

    fun despertarInspect(owner: String, repo: String) = launchOp(Source.DESPERTAR) {
        require(entitlement.isUnlocked()) { "O Despertar é um recurso pago e está bloqueado." }
        _repositoryStudy.value = despertar.inspectRepository(owner, repo)
        _notice.value = "Análise técnica de ${_repositoryStudy.value?.fullName} concluída."
    }

    fun exportStudyReport(): String? = _repositoryStudy.value?.report()

    fun despertarInspire(owner: String, repo: String, keywords: String, authorized: Boolean) =
        launchOp(Source.DESPERTAR) {
            requireDespertar(authorized)
            _inspirations.value = despertar.findInspirations(owner, repo, keywords)
            _notice.value = "${_inspirations.value.size} repositórios públicos estudados."
        }

    fun despertarCommit(owner: String, repo: String, authorized: Boolean) = launchOp(Source.DESPERTAR) {
        requireDespertar(authorized)
        require(_inspirations.value.isNotEmpty()) { "Execute primeiro a busca de inspirações." }
        val md = AttributionLedger.render("$owner/$repo", _inspirations.value, LocalDate.now().toString())
        val branch = despertar.commitLedger(owner, repo, md)
        _notice.value = "ATTRIBUTIONS.md gravado no ramo $branch. Reveja e faça o merge quando quiser."
    }

    fun generateAndroidProject(name: String, packageName: String, internet: Boolean) = launchOp(Source.DESPERTAR) {
        require(entitlement.isUnlocked()) { "O Despertar está bloqueado." }
        val blueprint = AndroidProjectBlueprint(name.trim(), packageName.trim(), includeInternetPermission = internet)
        blueprint.validate()
        val outputDir = File(getApplication<Application>().cacheDir, "generated").apply {
            if (!exists() && !mkdirs()) error("Não foi possível criar a pasta temporária de exportação.")
        }
        // Mantém no máximo os dois ZIPs mais recentes para evitar crescimento ilimitado do cache.
        outputDir.listFiles { file -> file.isFile && file.extension == "zip" }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(2)
            ?.forEach { it.delete() }
        val output = File(outputDir, "${System.currentTimeMillis()}-android-project.zip")
        val generated = withContext(Dispatchers.IO) { AndroidProjectGenerator.generate(output, blueprint) }
        _buildArtifactPath.value = null
        _projectInsight.value = null
        _architectureReport.value = null
        _fusionReport.value = null
        _codeSimilarityReport.value = null
        _generatedProject.value = generated
        _notice.value = "Projeto Android gerado: ${generated.name}. Partilhe o ZIP para o guardar." 
    }

    /** Analisa localmente o ZIP gerado sem executar código nem extrair entradas no armazenamento. */
    fun analyzeGeneratedProject() = launchOp(Source.DESPERTAR) {
        require(entitlement.isUnlocked()) { "O Despertar está bloqueado." }
        val source = requireNotNull(_generatedProject.value) { "Gere primeiro um projeto Android." }
        _projectInsight.value = withContext(Dispatchers.IO) { ProjectIntelligence.analyzeZip(source) }
        _notice.value = "Code Intelligence concluiu a análise estática local do projeto."
    }

    /** Importa um ZIP de referência para comparação; não extrai nem executa o seu conteúdo. */
    fun selectFusionSource(uri: Uri) = launchOp(Source.DESPERTAR) {
        require(entitlement.isUnlocked()) { "O Despertar está bloqueado." }
        val destination = File(getApplication<Application>().cacheDir, "fusion-reference.zip")
        _fusionSource.value = null
        _fusionReport.value = null
        _codeSimilarityReport.value = null
        withContext(Dispatchers.IO) {
            try {
                val input = getApplication<Application>().contentResolver.openInputStream(uri)
                    ?: error("Não foi possível abrir o ZIP selecionado.")
                input.use { source -> destination.outputStream().buffered().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var total = 0L
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 30L * 1024 * 1024) { "O ZIP de referência excede o limite de 30 MB." }
                        output.write(buffer, 0, count)
                    }
                } }
            } catch (error: Exception) {
                destination.delete()
                throw error
            }
        }
        require(destination.isFile && destination.length() >= 4L) { "O ficheiro selecionado está vazio ou inválido." }
        _fusionSource.value = destination
        _fusionReport.value = null
        _notice.value = "ZIP de referência carregado. A análise continua sem executar ou extrair código."
    }

    /** Compara o projeto gerado com o ZIP de referência e prepara um plano, sem efetuar a fusão. */
    fun analyzeComponentFusion() = launchOp(Source.DESPERTAR) {
        require(entitlement.isUnlocked()) { "O Despertar está bloqueado." }
        val primary = requireNotNull(_generatedProject.value) { "Gere primeiro o projeto principal." }
        val reference = requireNotNull(_fusionSource.value) { "Selecione primeiro um ZIP de projeto de referência." }
        _fusionReport.value = withContext(Dispatchers.IO) { ComponentFusion.analyzeZip(primary, reference) }
        _notice.value = "Component Fusion concluiu a comparação. Nenhum ficheiro foi fundido ou alterado."
    }

    /** Mede similaridade lexical e estrutural entre fontes; somente leitura, sem refatoração automática. */
    fun compareCodeSimilarity() = launchOp(Source.DESPERTAR) {
        require(entitlement.isUnlocked()) { "O Despertar está bloqueado." }
        val primary = requireNotNull(_generatedProject.value) { "Gere primeiro o projeto principal." }
        val reference = requireNotNull(_fusionSource.value) { "Selecione primeiro um ZIP de referência." }
        _codeSimilarityReport.value = withContext(Dispatchers.IO) {
            val primaryFiles = ProjectIntelligence.readRelevantFiles(primary)
            val referenceFiles = ProjectIntelligence.readRelevantFiles(reference)
            CodeSimilarityEngine.compare(primaryFiles, referenceFiles)
        }
        _notice.value = "Medição de similaridade concluída. Nenhum código foi alterado."
    }

    /** Produz um mapa de dependências e um plano arquitetural sem modificar o projeto. */
    fun architectGeneratedProject() = launchOp(Source.DESPERTAR) {
        require(entitlement.isUnlocked()) { "O Despertar está bloqueado." }
        val source = requireNotNull(_generatedProject.value) { "Gere primeiro um projeto Android." }
        _architectureReport.value = withContext(Dispatchers.IO) { ProjectArchitect.analyzeZip(source) }
        _notice.value = "Project Architect concluiu o mapa estrutural e o plano recomendado. Nenhum ficheiro foi alterado."
    }

    /** Exporta o projeto gerado para armazenamento partilhado e compila-o no Termux. */
    fun buildGeneratedAndroidProject() = launchOp(Source.DESPERTAR) {
        require(entitlement.isUnlocked()) { "O Despertar está bloqueado." }
        require(_termuxResult.value?.isPending != true) { "Aguarde a operação Termux anterior." }
        val source = requireNotNull(_generatedProject.value) { "Gere primeiro um projeto Android." }
        val key = "hubops-${System.currentTimeMillis()}"
        val exported = withContext(Dispatchers.IO) {
            ProjectZipExporter.export(getApplication<Application>(), source, key)
        }
        _buildArtifactPath.value = null
        _buildRepairFindings.value = emptyList()
        val id = withContext(Dispatchers.IO) {
            termux.run(TermuxJobs.buildAndroidProject(exported.sharedPath, key, exported.sha256))
        }
        _termuxResult.value = TermuxRunResult(id, -2, "Projeto exportado; compilação Android iniciada no Termux…", "", "", -2, 0L)
        _notice.value = "Compilação iniciada. ZIP: ${exported.displayName} (${exported.sizeBytes / 1024} KB), SHA-256 ${exported.sha256}. O APK só será apresentado após validação estrutural e criptográfica."
    }

    fun importApk(uri: Uri) = launchOp(Source.DESPERTAR) {
        require(entitlement.isUnlocked()) { "O Despertar está bloqueado." }
        val staged = withContext(Dispatchers.IO) { apkIntake.import(uri) }
        _stagedApk.value = staged
        _notice.value = "APK importado para Downloads/HubOps. SHA-256: ${staged.sha256}"
    }

    fun refreshTermuxResult() {
        val result = TermuxResultService.read(getApplication())
        _termuxResult.value = result
        // Só expõe um APK quando callback, marcador, relatório, hash e package estão presentes.
        _buildArtifactPath.value = BuildArtifactParser.parse(result)
        _buildRepairFindings.value = if (result == null || result.isPending) emptyList() else
            BuildRepairAdvisor.analyze(result.stdout, result.stderr + "\n" + result.internalError, result.exitCode)
    }

    fun despertarCheckTermux() = launchOp(Source.DESPERTAR) {
        require(entitlement.isUnlocked()) { "O Despertar está bloqueado." }
        val id = withContext(Dispatchers.IO) { termux.run(TermuxJobs.environmentCheck()) }
        _termuxResult.value = TermuxRunResult(id, -2, "Diagnóstico iniciado…", "", "", -2, 0L)
        _notice.value = "Diagnóstico do Termux iniciado. Aguarde o resultado."
    }

    fun despertarInspectOutput(outName: String, authorized: Boolean) = launchOp(Source.DESPERTAR) {
        requireDespertar(authorized)
        require(_termuxResult.value?.isPending != true) { "Aguarde a operação Termux anterior." }
        val id = withContext(Dispatchers.IO) { termux.run(TermuxJobs.inspectOutput(outName)) }
        _termuxResult.value = TermuxRunResult(id, -2, "Inspeção dos resultados iniciada…", "", "", -2, 0L)
        _notice.value = "Inspeção da pasta de resultados iniciada."
    }

    fun despertarReverse(tool: String, outName: String, authorized: Boolean) =
        launchOp(Source.DESPERTAR) {
            requireDespertar(authorized)
            require(_termuxResult.value?.isPending != true) { "Já existe uma análise Termux em execução; aguarde o resultado." }
            val staged = requireNotNull(_stagedApk.value) { "Escolha e importe primeiro um APK." }
            val executionId = termux.run(TermuxJobs.build(tool, staged.sharedPath, outName))
            refreshTermuxResult()
            _notice.value = "Análise enviada ao Termux (execução #$executionId). A app receberá o código de saída e os registos quando terminar."
        }

    fun setDevUnlocked(v: Boolean) {
        config.devUnlocked = v
    }

    fun clearNotice() {
        _notice.value = null
    }
}
