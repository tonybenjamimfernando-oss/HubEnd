package dev.hubops.core.integration

import java.io.File
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.Properties
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.util.zip.ZipFile
import dev.hubops.core.recovery.RecoveryState
import dev.hubops.core.recovery.TransactionRecoveryEngine
import dev.hubops.core.repair.ControlledRepairEngine
import dev.hubops.core.repair.RepairPlan
import dev.hubops.core.repair.RepairResult

/** Engines remain autonomous; the orchestrator coordinates only versioned artifacts and approvals. */
enum class EngineId { DESPERTAR, ESPELHAR_X, VISLUMBOPS, HUBOPS }
enum class TransferStatus { PREPARED, IMPORTED_TO_STAGING, VALIDATION_BLOCKED, ACCEPTED, FAILED }

data class ArtifactProvenance(
    val sourceEngine: EngineId,
    val artifactName: String,
    val artifactSha256: String,
    val createdAtEpochMs: Long,
)

data class TransferImpact(
    val destinationPrefix: String,
    val supportedFiles: Int,
    val ignoredFiles: Int,
    val collisions: List<String>,
    val expandedBytes: Long,
    val riskNotes: List<String>,
)

data class TransferProposal(
    val id: String,
    val provenance: ArtifactProvenance,
    val destinationEngine: EngineId,
    val impact: TransferImpact,
    val status: TransferStatus,
    val createdAtEpochMs: Long,
)

data class ValidationEvidence(
    val filesChecked: Int,
    val expectedImportedFiles: Int,
    val errors: Int,
    val warnings: Int,
    val astExpectedFiles: Int,
    val astParsedFiles: Int,
    val astUnavailableFiles: Int,
    val astSyntaxErrorFiles: Int,
    val astTruncatedFiles: Int,
    val filesOmitted: Int,
    val evidenceSha256: String,
    val limitations: List<String>,
) {
    val accepted: Boolean get() = filesChecked >= expectedImportedFiles && errors == 0 && astSyntaxErrorFiles == 0 && astUnavailableFiles == 0 &&
        astTruncatedFiles == 0 && filesOmitted == 0 && (astExpectedFiles == 0 || astParsedFiles >= astExpectedFiles)
}

data class ProjectRecord(
    val proposalId: String,
    val sourceEngine: EngineId,
    val destinationEngine: EngineId,
    val artifactName: String,
    val artifactSha256: String,
    val destinationPrefix: String,
    val acceptedFiles: Int,
    val validationEvidenceSha256: String,
    val acceptedAtEpochMs: Long,
)

data class TransferOutcome(
    val proposal: TransferProposal,
    val importedFiles: Int,
    val promotedFiles: Int,
    val validation: ValidationEvidence,
    val accepted: Boolean,
    val message: String,
)

enum class ReconciliationStatus {
    CONSISTENT_COMMITTED, STAGING_AVAILABLE_FOR_REVIEW, SOURCE_ARTIFACT_MISSING_OR_CHANGED,
    ORPHANED_TRANSACTION, LEDGER_DESTINATION_MISMATCH, NEEDS_MANUAL_REVIEW
}

data class RecoveryReconciliation(
    val transactionId: String,
    val proposalId: String?,
    val transactionState: RecoveryState,
    val status: ReconciliationStatus,
    val artifactAvailable: Boolean,
    val stagingAvailable: Boolean,
    val destinationAvailable: Boolean,
    val ledgerRecordAvailable: Boolean,
    val explanation: String,
)

/**
 * Durable audit log + two-phase transfer gate.
 * No engine may mutate the other's workspace through this class without provenance,
 * impact review, explicit commit, and validation evidence.
 */
class HubOpsOrchestrator(private val stateDirectory: File) {
    private val logFile = File(stateDirectory, "integration-ledger.log")
    private val pendingDirectory = File(stateDirectory, "pending")
    private val artifactsDirectory = File(stateDirectory, "artifacts")
    private val recoveryEngine = TransactionRecoveryEngine(stateDirectory)
    private val crossEngineAssessment = CrossEngineAssessmentCoordinator()
    private val pending = linkedMapOf<String, TransferProposal>()
    private val supported = setOf("kt", "kts", "java", "py", "js", "ts", "json", "xml", "sh", "md", "rs", "go", "dart", "gradle", "properties", "toml", "yaml", "yml")

    init {
        require(stateDirectory.exists() || stateDirectory.mkdirs()) { "Não foi possível criar o registo de integração." }
        require(pendingDirectory.exists() || pendingDirectory.mkdirs()) { "Não foi possível criar a pasta de propostas pendentes." }
        require(artifactsDirectory.exists() || artifactsDirectory.mkdirs()) { "Não foi possível criar a pasta de artefactos de integração." }
    }

    @Synchronized
    fun prepareZipTransfer(
        archive: File,
        sourceEngine: EngineId,
        destinationEngine: EngineId,
        destinationPrefix: String,
        destinationWorkspace: File,
    ): TransferProposal {
        require(sourceEngine != destinationEngine) { "A transferência exige motores distintos." }
        HubOpsEngineRegistry.requireFlow(sourceEngine, destinationEngine, EngineFlowMode.GOVERNED_MUTATION)
        require(pendingProposals().size < 5) { "Existem cinco propostas pendentes; conclua ou cancele uma antes de criar outra." }
        require(archive.isFile && archive.length() in 1..(30L * 1024 * 1024)) { "Artefacto ZIP inexistente ou fora do limite de 30 MB." }
        val prefix = validateRelative(destinationPrefix)
        val sourceHashBeforeScan = sha256(archive)
        val workspace = destinationWorkspace.canonicalFile
        val destination = File(workspace, prefix).canonicalFile
        require(destination.path.startsWith(workspace.path + File.separator)) { "Destino fora do workspace." }
        val names = linkedSetOf<String>()
        val collisions = mutableListOf<String>()
        var supportedCount = 0
        var ignoredCount = 0
        var expanded = 0L
        var entries = 0
        ZipFile(archive).use { zip ->
            val iterator = zip.entries()
            while (iterator.hasMoreElements()) {
                val entry = iterator.nextElement()
                entries++
                require(entries <= 1_000) { "ZIP excede 1.000 entradas." }
                if (entry.isDirectory) continue
                val raw = entry.name.replace('\\', '/')
                require(!raw.startsWith('/') && raw.none { it.isISOControl() } && raw.split('/').none { it.isBlank() || it == "." || it == ".." || it.startsWith(".") }) {
                    "ZIP contém caminho absoluto, oculto ou traversal: $raw"
                }
                require(names.add(raw)) { "ZIP contém entrada duplicada: $raw" }
                val ext = raw.substringAfterLast('.', "").lowercase()
                val isSupported = ext in supported
                if (isSupported) {
                    supportedCount++
                    val target = File(destination, raw).canonicalFile
                    require(target.path.startsWith(destination.path + File.separator)) { "Entrada sai do destino: $raw" }
                    if (target.exists()) collisions += raw
                } else ignoredCount++
                var entryBytes = 0L
                zip.getInputStream(entry).buffered().use { input ->
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        entryBytes += read
                        expanded += read
                        require(expanded <= 20L * 1024 * 1024) { "Conteúdo expandido excede 20 MB." }
                        if (isSupported) require(entryBytes <= 500L * 1024) { "Fonte superior a 500 KB: $raw" }
                    }
                }
            }
        }
        require(supportedCount in 1..200) { "O ZIP precisa conter entre 1 e 200 ficheiros de código suportados." }
        require(sha256(archive) == sourceHashBeforeScan) { "O artefacto mudou durante a análise de impacto; prepare novamente." }
        val proposalId = UUID.randomUUID().toString()
        val storedArtifact = File(artifactsDirectory, "$proposalId.zip")
        val tempArtifact = File(artifactsDirectory, ".$proposalId.${System.nanoTime()}.tmp")
        try {
            archive.copyTo(tempArtifact, overwrite = false)
            require(sha256(tempArtifact) == sourceHashBeforeScan) { "A cópia privada do artefacto não corresponde à origem analisada." }
            Files.move(tempArtifact.toPath(), storedArtifact.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tempArtifact.toPath(), storedArtifact.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally { tempArtifact.delete() }
        val proposal = TransferProposal(
            id = proposalId,
            provenance = ArtifactProvenance(sourceEngine, archive.name, sourceHashBeforeScan, System.currentTimeMillis()),
            destinationEngine = destinationEngine,
            impact = TransferImpact(
                destinationPrefix = prefix,
                supportedFiles = supportedCount,
                ignoredFiles = ignoredCount,
                collisions = collisions,
                expandedBytes = expanded,
                riskNotes = listOf(
                    "O conteúdo será importado primeiro para uma área de staging.",
                    "A aceitação exige validação estática e AST quando houver linguagens suportadas.",
                    "Nenhum ficheiro existente pode ser substituído.",
                ),
            ),
            status = TransferStatus.PREPARED,
            createdAtEpochMs = System.currentTimeMillis(),
        )
        pending[proposal.id] = proposal
        persistPending(proposal)
        append(proposal.id, "PREPARED", "${sourceEngine}->${destinationEngine};sha256=${proposal.provenance.artifactSha256};files=$supportedCount;collisions=${collisions.size}")
        return proposal
    }

    @Synchronized
    fun executeTransfer(
        proposalId: String,
        archive: File,
        stagingPrefix: String,
        importToStaging: (File, String) -> Int,
        validateStaging: (String) -> ValidationEvidence,
        promoteStaging: (String, String) -> Int,
    ): TransferOutcome {
        val proposal = pending[proposalId] ?: loadPending(proposalId) ?: error("Proposta inexistente ou já processada.")
        require(proposal.status == TransferStatus.PREPARED) { "A proposta já não está preparada." }
        require(proposal.impact.collisions.isEmpty()) { "Transferência bloqueada: existem colisões no destino." }
        require(archive.isFile && sha256(archive) == proposal.provenance.artifactSha256) {
            "O artefacto mudou desde a análise de origem. Prepare uma nova proposta."
        }
        val stage = validateRelative(stagingPrefix)
        var promotionCompleted = false
        val transaction = recoveryEngine.begin(
            operation = "zip-transfer",
            source = "artifacts/$proposalId.zip",
            destination = proposal.impact.destinationPrefix,
            detail = "Transferência governada ${proposal.provenance.sourceEngine}->${proposal.destinationEngine}; staging=$stage",
        )
        return try {
            recoveryEngine.transition(transaction.id, RecoveryState.APPLYING, "Importação para staging iniciada.")
            val imported = importToStaging(archive, stage)
            require(imported == proposal.impact.supportedFiles) {
                "A contagem importada diverge do impacto aprovado (${proposal.impact.supportedFiles} esperado, $imported recebido)."
            }
            val stagedProposal = proposal.copy(status = TransferStatus.IMPORTED_TO_STAGING)
            pending[proposalId] = stagedProposal
            persistPending(stagedProposal)
            append(proposalId, "IMPORTED_TO_STAGING", "files=$imported;stage=$stage")
            recoveryEngine.transition(transaction.id, RecoveryState.VALIDATING, "Staging importado; validação iniciada.")
            val evidence = validateStaging(stage)
            if (!evidence.accepted) {
                val blocked = stagedProposal.copy(status = TransferStatus.VALIDATION_BLOCKED)
                pending[proposalId] = blocked
                persistPending(blocked)
                append(proposalId, "VALIDATION_BLOCKED", "errors=${evidence.errors};astUnavailable=${evidence.astUnavailableFiles};astErrors=${evidence.astSyntaxErrorFiles}")
                recoveryEngine.transition(transaction.id, RecoveryState.NEEDS_ATTENTION, "Validação bloqueou a promoção; staging preservado para inspeção: $stage")
                return TransferOutcome(blocked, imported, 0, evidence, false,
                    "Importação isolada em staging; não foi promovida porque a validação não cumpriu os critérios de aceitação.")
            }
            recoveryEngine.transition(transaction.id, RecoveryState.COMMITTING, "Validação aceite; promoção iniciada.")
            val promoted = promoteStaging(stage, proposal.impact.destinationPrefix)
            promotionCompleted = true
            require(promoted == imported) { "A promoção não transferiu todos os ficheiros; operação incompleta." }
            val acceptedAt = System.currentTimeMillis()
            val record = ProjectRecord(
                proposalId = proposal.id,
                sourceEngine = proposal.provenance.sourceEngine,
                destinationEngine = proposal.destinationEngine,
                artifactName = proposal.provenance.artifactName,
                artifactSha256 = proposal.provenance.artifactSha256,
                destinationPrefix = proposal.impact.destinationPrefix,
                acceptedFiles = promoted,
                validationEvidenceSha256 = evidence.evidenceSha256,
                acceptedAtEpochMs = acceptedAt,
            )
            // A aceitação e o registo de projeto são uma única entrada de ledger.
            append(proposalId, "ACCEPTED", encodeProjectRecord(record))
            recoveryEngine.transition(transaction.id, RecoveryState.COMMITTED, "Promoção e registo de aceitação concluídos; $promoted ficheiros.")
            val accepted = stagedProposal.copy(status = TransferStatus.ACCEPTED)
            pending.remove(proposalId)
            pendingFile(proposalId).delete()
            artifactFile(proposalId)?.delete()
            TransferOutcome(accepted, imported, promoted, evidence, true,
                "Transferência aceite após validação; origem, impacto e evidência foram registados.")
        } catch (failure: Exception) {
            // Se a promoção já ocorreu mas o registo falhou, tenta devolver o diretório ao staging.
            if (promotionCompleted) runCatching { promoteStaging(proposal.impact.destinationPrefix, stage) }
            runCatching {
                val current = recoveryEngine.transactions(500).firstOrNull { it.id == transaction.id }
                if (current != null && current.state !in setOf(RecoveryState.COMMITTED, RecoveryState.ROLLED_BACK, RecoveryState.NEEDS_ATTENTION)) {
                    recoveryEngine.transition(transaction.id, RecoveryState.NEEDS_ATTENTION,
                        "Falha durante transferência; reconciliação necessária. ${failure.message ?: failure.javaClass.simpleName}")
                }
            }
            val failed = proposal.copy(status = TransferStatus.FAILED)
            pending[proposalId] = failed
            persistPending(failed)
            append(proposalId, "FAILED", failure.message ?: failure.javaClass.simpleName)
            throw failure
        }
    }

    fun artifactFile(proposalId: String): File? {
        val file = runCatching {
            require(proposalId.matches(Regex("[A-Fa-f0-9-]{36}")))
            File(artifactsDirectory, "$proposalId.zip").canonicalFile
        }.getOrNull() ?: return null
        return file.takeIf { it.path.startsWith(artifactsDirectory.canonicalPath + File.separator) && it.isFile }
    }

    private fun pendingFile(id: String): File {
        require(id.matches(Regex("[A-Fa-f0-9-]{36}"))) { "Identificador de proposta inválido." }
        return File(pendingDirectory, "$id.properties")
    }

    private fun persistPending(proposal: TransferProposal) {
        val target = pendingFile(proposal.id)
        val temp = File(pendingDirectory, ".${proposal.id}.${System.nanoTime()}.tmp")
        val props = Properties().apply {
            setProperty("id", proposal.id)
            setProperty("sourceEngine", proposal.provenance.sourceEngine.name)
            setProperty("artifactName", proposal.provenance.artifactName)
            setProperty("artifactSha256", proposal.provenance.artifactSha256)
            setProperty("sourceCreatedAt", proposal.provenance.createdAtEpochMs.toString())
            setProperty("destinationEngine", proposal.destinationEngine.name)
            setProperty("destinationPrefix", proposal.impact.destinationPrefix)
            setProperty("supportedFiles", proposal.impact.supportedFiles.toString())
            setProperty("ignoredFiles", proposal.impact.ignoredFiles.toString())
            setProperty("collisions", proposal.impact.collisions.joinToString("\n"))
            setProperty("expandedBytes", proposal.impact.expandedBytes.toString())
            setProperty("riskNotes", proposal.impact.riskNotes.joinToString("\n"))
            setProperty("status", proposal.status.name)
            setProperty("createdAt", proposal.createdAtEpochMs.toString())
        }
        try {
            temp.outputStream().use { props.store(it, "HubOps pending integration proposal") }
            try { Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        } finally { temp.delete() }
    }

    private fun loadPending(id: String): TransferProposal? {
        val file = runCatching { pendingFile(id) }.getOrNull()?.takeIf { it.isFile } ?: return null
        return runCatching {
            val props = Properties().apply { file.inputStream().use { load(it) } }
            val proposal = TransferProposal(
                id = props.getProperty("id"),
                provenance = ArtifactProvenance(
                    EngineId.valueOf(props.getProperty("sourceEngine")), props.getProperty("artifactName"),
                    props.getProperty("artifactSha256"), props.getProperty("sourceCreatedAt").toLong(),
                ),
                destinationEngine = EngineId.valueOf(props.getProperty("destinationEngine")),
                impact = TransferImpact(
                    props.getProperty("destinationPrefix"), props.getProperty("supportedFiles").toInt(),
                    props.getProperty("ignoredFiles").toInt(), props.getProperty("collisions", "").split('\n').filter(String::isNotBlank),
                    props.getProperty("expandedBytes").toLong(), props.getProperty("riskNotes", "").split('\n').filter(String::isNotBlank),
                ),
                status = TransferStatus.valueOf(props.getProperty("status")),
                createdAtEpochMs = props.getProperty("createdAt").toLong(),
            )
            pending[id] = proposal
            proposal
        }.getOrNull()
    }

    @Synchronized
    fun cancelProposal(proposalId: String): Boolean {
        val proposal = pending[proposalId] ?: loadPending(proposalId) ?: return false
        pending.remove(proposalId)
        pendingFile(proposalId).delete()
        artifactFile(proposalId)?.delete()
        append(proposalId, "CANCELLED", "${proposal.status};destination=${proposal.impact.destinationPrefix}")
        return true
    }

    /** Records a VislumbOps verification outcome in the same append-only HubOps ledger.
     * The record is informational: it never mutates a project or upgrades a failed result.
     */
    @Synchronized
    fun recordExternalVerification(
        sourceEngine: EngineId,
        destinationEngine: EngineId,
        verificationId: String,
        artifactSha256: String,
        reportSha256: String,
        outcome: String,
        checks: Int,
        findings: Int,
    ) {
        require(sourceEngine != destinationEngine && verificationId.isNotBlank())
        require(artifactSha256.matches(Regex("[a-fA-F0-9]{64}")))
        require(reportSha256.matches(Regex("[a-fA-F0-9]{64}")))
        require(outcome in setOf("APPROVED", "REMEDIATION_REQUIRED", "REJECTED"))
        require(checks >= 0 && findings >= 0)
        append(UUID.randomUUID().toString(), "EXTERNAL_VERIFICATION",
            "$sourceEngine->$destinationEngine;verification=$verificationId;artifactSha256=$artifactSha256;reportSha256=$reportSha256;outcome=$outcome;checks=$checks;findings=$findings;mutations=0")
    }

    @Synchronized
    fun recordReadOnlyInspection(
        sourceEngine: EngineId,
        destinationEngine: EngineId,
        sourceName: String,
        sourceSha256: String,
        reportSha256: String,
        filesInspected: Int,
    ) {
        require(sourceEngine != destinationEngine && sourceName.isNotBlank() && filesInspected >= 0)
        HubOpsEngineRegistry.requireFlow(sourceEngine, destinationEngine, EngineFlowMode.READ_ONLY_ANALYSIS)
        require(sourceSha256.matches(Regex("[a-fA-F0-9]{64}")) && reportSha256.matches(Regex("[a-fA-F0-9]{64}")))
        append(UUID.randomUUID().toString(), "READ_ONLY_INSPECTION",
            "$sourceEngine->$destinationEngine;source=$sourceName;sourceSha256=$sourceSha256;reportSha256=$reportSha256;files=$filesInspected;mutations=0")
    }

    /** Detecta operações interrompidas sem alterar ficheiros de projetos ou staging. */
    fun inspectInterruptedTransactions() = recoveryEngine.recoverInterrupted()

    /**
     * Cruza diário, proposta persistida, artefacto privado, staging e ledger.
     * É estritamente read-only: não promove, elimina, renomeia nem reverte ficheiros.
     * stagingWorkspace deve ser a raiz real do workspace do Espelhar-X.
     */
    @Synchronized
    fun reconcileInterruptedTransactions(stagingWorkspace: File): List<RecoveryReconciliation> {
        val stagingRoot = stagingWorkspace.canonicalFile
        val records = recentProjectRecords(100).associateBy { it.proposalId }
        return recoveryEngine.transactions(500).map { tx ->
            val proposalId = tx.source.removePrefix("artifacts/").removeSuffix(".zip")
                .takeIf { tx.source.startsWith("artifacts/") && it.matches(Regex("[A-Fa-f0-9-]{36}")) }
            val proposal = proposalId?.let { pending[it] ?: loadPending(it) }
            val artifact = proposalId?.let { artifactFile(it) }
            val artifactAvailable = artifact?.isFile == true && proposal != null &&
                runCatching { sha256(artifact) == proposal.provenance.artifactSha256 }.getOrDefault(false)
            val stage = proposalId?.let { File(stagingRoot, "integration-staging/$it").canonicalFile }
            val stageInsideRoot = stage != null && stage.path.startsWith(stagingRoot.path + File.separator)
            val stagingAvailable = stageInsideRoot && stage!!.exists()
            val ledger = proposalId?.let { records[it] }
            val destinationPrefix = proposal?.impact?.destinationPrefix ?: ledger?.destinationPrefix
            val destination = destinationPrefix?.let { File(stagingRoot, it).canonicalFile }
            val destinationInsideRoot = destination != null && destination.path.startsWith(stagingRoot.path + File.separator)
            val destinationAvailable = destinationInsideRoot && destination!!.exists()
            val status: ReconciliationStatus
            val explanation: String
            when {
                proposalId == null -> {
                    status = ReconciliationStatus.ORPHANED_TRANSACTION
                    explanation = "Não foi possível ligar a transação a uma proposta; nenhuma ação foi executada."
                }
                tx.state == RecoveryState.COMMITTED && ledger != null && destinationAvailable -> {
                    status = ReconciliationStatus.CONSISTENT_COMMITTED
                    explanation = "Diário, ledger de aceitação e destino estão presentes."
                }
                tx.state == RecoveryState.COMMITTED -> {
                    status = ReconciliationStatus.LEDGER_DESTINATION_MISMATCH
                    explanation = "A transação está concluída, mas falta o registo de aceitação ou o destino esperado."
                }
                proposal == null -> {
                    status = ReconciliationStatus.ORPHANED_TRANSACTION
                    explanation = "A proposta persistida não foi encontrada para uma operação ainda não concluída."
                }
                !artifactAvailable -> {
                    status = ReconciliationStatus.SOURCE_ARTIFACT_MISSING_OR_CHANGED
                    explanation = "O artefacto privado está ausente ou o SHA-256 diverge da proposta."
                }
                stagingAvailable -> {
                    status = ReconciliationStatus.STAGING_AVAILABLE_FOR_REVIEW
                    explanation = "Existe staging preservado; requer inspeção e decisão explícita antes de qualquer promoção ou limpeza."
                }
                else -> {
                    status = ReconciliationStatus.NEEDS_MANUAL_REVIEW
                    explanation = "Não há evidência suficiente para concluir o resultado; revisão manual necessária."
                }
            }
            RecoveryReconciliation(tx.id, proposalId, tx.state, status, artifactAvailable,
                stagingAvailable, destinationAvailable, ledger != null, explanation)
        }
    }

    /** Produz planos de recuperação verificáveis; nenhum plano executa mutações. */
    @Synchronized
    fun planRecovery(stagingWorkspace: File): List<RecoveryActionPlan> =
        reconcileInterruptedTransactions(stagingWorkspace).map { RecoveryActionPlanner.plan(it) }

    /**
     * Une a análise AST do ZIP-fonte (Espelhar-X/Despertar) com um contrato VislumbOps já concluído.
     * A operação é somente de leitura e exige uma referência de build fornecida explicitamente.
     */
    fun assessProjectAcrossEngines(
        sourceArchive: File,
        buildReference: String,
        vislumbContract: com.hubops.vislumbrops.core.VerificationContract,
    ): CrossEngineAssessment = crossEngineAssessment.assess(sourceArchive, buildReference, vislumbContract)

    /** Prepara uma correção de um único ficheiro dentro do workspace, sem alterar o destino. */
    @Synchronized
    fun prepareControlledRepair(workspaceRoot: File, relativeTarget: String, replacement: File): RepairPlan =
        ControlledRepairEngine(workspaceRoot, stateDirectory).prepare(relativeTarget, replacement)

    /** A aplicação exige o token de aprovação explícita devolvido no plano e revalida ambos os hashes. */
    @Synchronized
    fun approveAndApplyControlledRepair(workspaceRoot: File, plan: RepairPlan, approvalToken: String, replacement: File): RepairResult =
        ControlledRepairEngine(workspaceRoot, stateDirectory).approveAndApply(plan, approvalToken, replacement)

    /** Reverte apenas se o destino ainda tiver exatamente o hash aplicado e o backup estiver íntegro. */
    @Synchronized
    fun rollbackControlledRepair(workspaceRoot: File, planId: String): RepairResult =
        ControlledRepairEngine(workspaceRoot, stateDirectory).rollback(planId)

    fun recentTransactions(limit: Int = 30) = recoveryEngine.transactions(limit)

    @Synchronized
    fun pendingProposals(): List<TransferProposal> {
        pendingDirectory.listFiles { file -> file.isFile && file.extension == "properties" }
            .orEmpty().forEach { file -> loadPending(file.nameWithoutExtension) }
        return pending.values.sortedByDescending { it.createdAtEpochMs }
    }

    @Synchronized
    fun recentProjectRecords(limit: Int = 20): List<ProjectRecord> {
        if (!logFile.isFile) return emptyList()
        return logFile.readLines(Charsets.UTF_8).asReversed().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size != 4 || parts[2] != "ACCEPTED") return@mapNotNull null
            decodeProjectRecord(runCatching { String(Base64.getDecoder().decode(parts[3]), Charsets.UTF_8) }.getOrNull() ?: return@mapNotNull null)
        }.take(limit.coerceIn(1, 100)).reversed()
    }

    private fun encodeProjectRecord(record: ProjectRecord): String {
        val fields = listOf(record.proposalId, record.sourceEngine.name, record.destinationEngine.name,
            record.artifactName, record.artifactSha256, record.destinationPrefix,
            record.acceptedFiles.toString(), record.validationEvidenceSha256, record.acceptedAtEpochMs.toString())
        val encoded = fields.joinToString(".") { Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray(Charsets.UTF_8)) }
        return "project-record:$encoded"
    }

    private fun decodeProjectRecord(payload: String): ProjectRecord? {
        if (!payload.startsWith("project-record:")) return null
        return runCatching {
            val fields = payload.removePrefix("project-record:").split('.').map {
                String(Base64.getUrlDecoder().decode(it), Charsets.UTF_8)
            }
            ProjectRecord(
                proposalId = fields[0], sourceEngine = EngineId.valueOf(fields[1]),
                destinationEngine = EngineId.valueOf(fields[2]), artifactName = fields[3],
                artifactSha256 = fields[4], destinationPrefix = fields[5],
                acceptedFiles = fields[6].toInt(), validationEvidenceSha256 = fields[7],
                acceptedAtEpochMs = fields[8].toLong(),
            )
        }.getOrNull()
    }

    @Synchronized
    fun recentAuditLines(limit: Int = 30): List<String> {
        if (!logFile.isFile) return emptyList()
        return logFile.readLines(Charsets.UTF_8).takeLast(limit.coerceIn(1, 100))
            .mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size != 4) null else {
                    val raw = runCatching { String(Base64.getDecoder().decode(parts[3]), Charsets.UTF_8) }.getOrDefault("detalhe indisponível")
                    val detail = decodeProjectRecord(raw)?.let { "${it.sourceEngine}->${it.destinationEngine}; ${it.acceptedFiles} ficheiros; destino=${it.destinationPrefix}; origem=${it.artifactSha256}; validação=${it.validationEvidenceSha256}" } ?: raw
                    "${parts[0]} · ${parts[1]} · ${parts[2]} · $detail"
                }
            }
    }

    private fun append(id: String, event: String, detail: String) {
        val encoded = Base64.getEncoder().encodeToString(detail.toByteArray(Charsets.UTF_8))
        logFile.appendText("${System.currentTimeMillis()}\t$id\t$event\t$encoded\n", Charsets.UTF_8)
    }

    private fun validateRelative(value: String): String {
        require(value.isNotBlank() && !value.startsWith('/') && !value.contains('\\') && value.none { it.isISOControl() } && value.split('/').all {
            it.isNotBlank() && it != "." && it != ".." && !it.startsWith(".")
        }) { "Caminho relativo inválido." }
        return value
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
