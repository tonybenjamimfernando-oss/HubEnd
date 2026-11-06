package dev.hubops.core.integration

import com.hubops.vislumbrops.core.VerificationStatus
import dev.hubops.core.repair.RepairPlan
import dev.hubops.core.repair.RepairResult
import java.io.File
import java.security.MessageDigest
import java.util.Properties
import java.util.Base64
import java.util.UUID

/** Durable, append-only lifecycle for diagnosis -> evidence -> approval -> repair -> re-audit. */
enum class WorkflowPhase { SOURCE_ANALYZED, ASSESSED, REPAIR_PREPARED, REPAIR_APPLIED, REAUDIT_COMPLETE, NEEDS_REVIEW }

data class ProfessionalWorkflowCase(
    val id: String,
    val phase: WorkflowPhase,
    val buildReference: String,
    val sourceName: String,
    val workspacePrefix: String,
    val sourceSha256: String,
    val assessmentSha256: String?,
    val verificationId: String?,
    val verificationStatus: String?,
    val binaryArtifactSha256: String?,
    val repairPlanId: String?,
    val repairTarget: String?,
    val repairReplacementSha256: String?,
    val lastMessage: String,
    val eventCount: Int,
    val journalHeadSha256: String,
)

/** Stores only audit metadata; never stores APKs, source code, approval tokens or credentials. */
class ProfessionalWorkflowCoordinator(private val root: File) {
    private val casesDir = File(root, "professional-workflows")
    private val journal = File(casesDir, "events.chain")

    init {
        require(root.exists() || root.mkdirs()) { "Não foi possível criar a pasta de estado do HubOps." }
        require(!java.nio.file.Files.isSymbolicLink(root.toPath())) { "Pasta de estado não pode ser link simbólico." }
        require(casesDir.exists() || casesDir.mkdirs()) { "Não foi possível criar o arquivo de fluxos profissionais." }
        require(!java.nio.file.Files.isSymbolicLink(casesDir.toPath())) { "Pasta de fluxos não pode ser link simbólico." }
        require(casesDir.canonicalFile.path.startsWith(root.canonicalFile.path + File.separator)) { "Pasta de fluxos fora da raiz de estado." }
        recoverPendingWrites()
    }

    @Synchronized
    fun begin(buildReference: String, sourceName: String, sourceSha256: String, workspacePrefix: String): ProfessionalWorkflowCase {
        require(buildReference.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"))) { "Referência de build inválida." }
        require(sourceSha256.matches(Regex("(?i)[a-f0-9]{64}"))) { "SHA-256 da origem inválido." }
        require(validWorkspacePrefix(workspacePrefix)) { "Prefixo do workspace inválido." }
        val c = ProfessionalWorkflowCase(UUID.randomUUID().toString(), WorkflowPhase.SOURCE_ANALYZED,
            buildReference, sourceName.take(180), workspacePrefix, sourceSha256.lowercase(), null, null, null, null,
            null, null, null, "Origem analisada; aguarda auditoria binária.", 0, "0".repeat(64))
        return persist(c, "SOURCE_ANALYZED", c.lastMessage)
    }

    @Synchronized
    fun recordAssessment(caseId: String, assessmentSha256: String, verificationId: String,
                         status: VerificationStatus, requiresReview: Boolean, binaryArtifactSha256: String): ProfessionalWorkflowCase {
        val old = requireCase(caseId)
        require(old.phase in setOf(WorkflowPhase.SOURCE_ANALYZED, WorkflowPhase.ASSESSED, WorkflowPhase.NEEDS_REVIEW)) {
            "A avaliação não é permitida na fase ${old.phase}."
        }
        require(assessmentSha256.matches(Regex("(?i)[a-f0-9]{64}")))
        require(binaryArtifactSha256.matches(Regex("(?i)[a-f0-9]{64}")))
        val phase = if (requiresReview || status != VerificationStatus.PASSED) WorkflowPhase.NEEDS_REVIEW else WorkflowPhase.ASSESSED
        return persist(old.copy(phase = phase, assessmentSha256 = assessmentSha256.lowercase(),
            verificationId = verificationId, verificationStatus = status.name,
            binaryArtifactSha256 = binaryArtifactSha256.lowercase(),
            lastMessage = if (phase == WorkflowPhase.NEEDS_REVIEW) "Achados, cobertura ou proveniência exigem revisão explícita." else "Avaliação cruzada concluída."),
            "ASSESSMENT", "verification=$verificationId;status=${status.name};review=$requiresReview")
    }

    @Synchronized
    fun recordRepairPrepared(caseId: String, plan: RepairPlan): ProfessionalWorkflowCase {
        val old = requireCase(caseId)
        require(old.phase in setOf(WorkflowPhase.ASSESSED, WorkflowPhase.NEEDS_REVIEW)) { "A preparação de correção não é permitida na fase ${old.phase}." }
        return persist(old.copy(phase = WorkflowPhase.REPAIR_PREPARED, repairPlanId = plan.id,
            repairTarget = plan.relativeTarget, repairReplacementSha256 = plan.replacementSha256,
            lastMessage = "Plano preparado; nenhuma alteração foi aplicada. Aprovação humana pendente."),
            "REPAIR_PREPARED", "plan=${plan.id};target=${plan.relativeTarget};replacement=${plan.replacementSha256}")
    }

    @Synchronized
    fun invalidatePreparedRepair(caseId: String, reason: String): ProfessionalWorkflowCase {
        val old = requireCase(caseId)
        require(old.phase == WorkflowPhase.REPAIR_PREPARED) { "Não existe plano pendente para invalidar." }
        return persist(old.copy(phase = WorkflowPhase.NEEDS_REVIEW, repairPlanId = null,
            repairTarget = null, repairReplacementSha256 = null,
            lastMessage = "Plano pendente invalidado: ${reason.take(240)}. Nenhuma alteração foi aplicada."),
            "REPAIR_PLAN_INVALIDATED", reason)
    }

    @Synchronized
    fun recordRepairApplied(caseId: String, result: RepairResult): ProfessionalWorkflowCase {
        val old = requireCase(caseId)
        require(old.phase == WorkflowPhase.REPAIR_PREPARED && old.repairPlanId == result.plan.id) { "Plano aplicado não corresponde ao caso ativo." }
        require(result.state.name == "APPLIED") { "A reparação não foi confirmada como aplicada." }
        return persist(old.copy(phase = WorkflowPhase.REPAIR_APPLIED,
            lastMessage = "Correção aplicada com backup. É obrigatório reconstruir o APK e executar nova auditoria."),
            "REPAIR_APPLIED", "plan=${result.plan.id};backup=${result.backupRelativePath.orEmpty()}")
    }

    @Synchronized
    fun recordRepairRolledBack(caseId: String, result: RepairResult): ProfessionalWorkflowCase {
        val old = requireCase(caseId)
        require(old.phase == WorkflowPhase.REPAIR_APPLIED && old.repairPlanId == result.plan.id) {
            "A reversão não corresponde à reparação ativa deste caso."
        }
        require(result.state.name == "ROLLED_BACK") { "A reversão não foi confirmada pelo motor de reparação." }
        return persist(old.copy(phase = WorkflowPhase.NEEDS_REVIEW,
            lastMessage = "Correção revertida; backup verificado. Reexporte o workspace e execute nova análise/build antes de encerrar o caso."),
            "REPAIR_ROLLED_BACK", "plan=${result.plan.id};backup=${result.backupRelativePath.orEmpty()}")
    }

    @Synchronized
    fun recordReaudit(caseId: String, verificationId: String, status: VerificationStatus,
                      assessmentSha256: String, requiresReview: Boolean, sourceName: String, sourceSha256: String, buildReference: String, binaryArtifactSha256: String): ProfessionalWorkflowCase {
        val old = requireCase(caseId)
        require(old.phase == WorkflowPhase.REPAIR_APPLIED) { "A nova auditoria exige uma correção aplicada e uma nova build." }
        require(assessmentSha256.matches(Regex("(?i)[a-f0-9]{64}")))
        require(sourceSha256.matches(Regex("(?i)[a-f0-9]{64}")))
        require(binaryArtifactSha256.matches(Regex("(?i)[a-f0-9]{64}")))
        require(!binaryArtifactSha256.equals(old.binaryArtifactSha256, ignoreCase = true)) { "O APK da nova auditoria tem o mesmo SHA-256 da build anterior; selecione o APK reconstruído." }
        require(buildReference.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")))
        require(buildReference != old.buildReference) { "A nova auditoria exige uma referência de build distinta." }
        val passed = status == VerificationStatus.PASSED && !requiresReview
        return persist(old.copy(phase = if (passed) WorkflowPhase.REAUDIT_COMPLETE else WorkflowPhase.NEEDS_REVIEW,
            buildReference = buildReference, sourceName = sourceName.take(180), sourceSha256 = sourceSha256.lowercase(),
            assessmentSha256 = assessmentSha256.lowercase(), verificationId = verificationId,
            verificationStatus = status.name, binaryArtifactSha256 = binaryArtifactSha256.lowercase(),
            lastMessage = if (passed) "Nova auditoria aprovada; ciclo de correção concluído." else "Nova auditoria mantém achados; caso permanece aberto para revisão."),
            "RE_AUDIT", "build=$buildReference;verification=$verificationId;status=${status.name};review=$requiresReview")
    }

    @Synchronized
    fun latest(): ProfessionalWorkflowCase? {
        runCatching { recoverPendingWrites() }.getOrElse { return null }
        val newest = casesDir.listFiles { f -> f.isFile && f.extension == "properties" }
            .orEmpty().maxByOrNull { it.lastModified() } ?: return null
        return readCase(newest) // fail closed; never silently fall back past a corrupted newest case
    }

    private fun requireCase(id: String): ProfessionalWorkflowCase {
        recoverPendingWrites()
        return readCase(File(casesDir, "$id.properties"))
            ?: error("Caso de fluxo não encontrado: $id")
    }

    private fun persist(c: ProfessionalWorkflowCase, event: String, detail: String): ProfessionalWorkflowCase {
        recoverPendingWrites()
        require(c.eventCount < 1_000) { "Limite de 1.000 eventos por caso atingido; abra um novo caso." }
        require(!journal.exists() || journal.length() <= 10L * 1024 * 1024) { "Diário excede o limite de 10 MB; exporte e arquive antes de continuar." }
        val previous = c.journalHeadSha256
        val snapshot = c.copy(eventCount = c.eventCount + 1)
        val snapshotHash = snapshotHash(snapshot)
        val payload = listOf(c.id, snapshot.eventCount, event, detail, previous, snapshotHash).joinToString("|")
        val head = sha256(payload)
        val updated = snapshot.copy(journalHeadSha256 = head)
        val encoder = Base64.getEncoder()
        val line = listOf(c.id, updated.eventCount.toString(),
            encoder.encodeToString(event.toByteArray(Charsets.UTF_8)),
            encoder.encodeToString(detail.toByteArray(Charsets.UTF_8)), previous, snapshotHash, head).joinToString("|")

        // Write-ahead transaction: a pending record lets startup finish either half of
        // the journal/snapshot commit after process death, without duplicating events.
        val pending = caseProperties(updated).apply { setProperty("pendingLine", line) }
        val pendingPayload = pending.stringPropertyNames().sorted()
            .joinToString("\n") { "$it=${pending.getProperty(it)}" }
        pending.setProperty("pendingHash", sha256(pendingPayload))
        val pendingFile = File(casesDir, "${updated.id}.pending")
        writePropertiesAtomically(pending, pendingFile, "HubOps pending workflow commit")
        recoverPendingWrites()
        return updated
    }

    private fun recoverPendingWrites() {
        val pendingFiles = casesDir.listFiles { file -> file.isFile && file.extension == "pending" }.orEmpty()
        pendingFiles.forEach { pendingFile ->
            require(!java.nio.file.Files.isSymbolicLink(pendingFile.toPath())) { "Registo pendente não pode ser link simbólico." }
            val p = Properties().apply { pendingFile.inputStream().use { load(it) } }
            val expectedHash = p.getProperty("pendingHash") ?: error("Hash do registo pendente ausente.")
            val payload = p.stringPropertyNames().filter { it != "pendingHash" }.sorted()
                .joinToString("\n") { "$it=${p.getProperty(it)}" }
            require(expectedHash == sha256(payload)) { "Registo pendente corrompido; recuperação bloqueada." }
            val updated = caseFromProperties(p)
            val line = p.getProperty("pendingLine") ?: error("Evento pendente ausente.")
            val parts = line.split('|')
            require(parts.size == 7 && parts[0] == updated.id && parts[1].toInt() == updated.eventCount && parts[6] == updated.journalHeadSha256) {
                "Evento pendente não corresponde ao snapshot; recuperação bloqueada."
            }
            val rows = if (journal.isFile) journal.readLines(Charsets.UTF_8).filter { it.startsWith(updated.id + "|") } else emptyList()
            when {
                rows.size == updated.eventCount && rows.last() == line -> Unit // append already reached disk
                rows.size == updated.eventCount - 1 -> {
                    val previousHead = if (rows.isEmpty()) "0".repeat(64) else rows.last().split('|').getOrNull(6)
                    require(previousHead == parts[4]) { "Cabeça anterior do diário diverge; recuperação bloqueada." }
                    appendJournalLine(line)
                }
                else -> error("Diário e registo pendente divergentes; recuperação bloqueada.")
            }
            writePropertiesAtomically(caseProperties(updated), File(casesDir, "${updated.id}.properties"), "HubOps Professional Workflow")
            require(verifyJournal(updated)) { "Diário não passou na verificação após recuperação." }
            require(pendingFile.delete()) { "Não foi possível limpar o marcador de commit concluído." }
        }
    }

    private fun appendJournalLine(line: String) {
        java.io.FileOutputStream(journal, true).use { stream ->
            stream.write((line + "\n").toByteArray(Charsets.UTF_8))
            stream.flush()
            stream.fd.sync()
        }
    }

    private fun writePropertiesAtomically(properties: Properties, target: File, comment: String) {
        val temp = File(casesDir, ".${target.name}.${UUID.randomUUID()}.tmp")
        java.io.FileOutputStream(temp).use { stream ->
            properties.store(stream, comment)
            stream.flush()
            stream.fd.sync()
        }
        try {
            java.nio.file.Files.move(temp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            java.nio.file.Files.move(temp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun caseProperties(c: ProfessionalWorkflowCase): Properties = Properties().apply {
        setProperty("id", c.id); setProperty("phase", c.phase.name)
        setProperty("buildReference", c.buildReference); setProperty("sourceName", c.sourceName)
        setProperty("workspacePrefix", c.workspacePrefix)
        setProperty("sourceSha256", c.sourceSha256); setProperty("assessmentSha256", c.assessmentSha256.orEmpty())
        setProperty("verificationId", c.verificationId.orEmpty()); setProperty("verificationStatus", c.verificationStatus.orEmpty())
        setProperty("binaryArtifactSha256", c.binaryArtifactSha256.orEmpty())
        setProperty("repairPlanId", c.repairPlanId.orEmpty()); setProperty("repairTarget", c.repairTarget.orEmpty())
        setProperty("repairReplacementSha256", c.repairReplacementSha256.orEmpty())
        setProperty("lastMessage", c.lastMessage); setProperty("eventCount", c.eventCount.toString())
        setProperty("journalHeadSha256", c.journalHeadSha256)
    }

    private fun caseFromProperties(p: Properties): ProfessionalWorkflowCase = ProfessionalWorkflowCase(
        p.getProperty("id"), WorkflowPhase.valueOf(p.getProperty("phase")),
        p.getProperty("buildReference"), p.getProperty("sourceName"), p.getProperty("workspacePrefix"), p.getProperty("sourceSha256"),
        p.getProperty("assessmentSha256").orEmpty().ifBlank { null }, p.getProperty("verificationId").orEmpty().ifBlank { null },
        p.getProperty("verificationStatus").orEmpty().ifBlank { null }, p.getProperty("binaryArtifactSha256").orEmpty().ifBlank { null },
        p.getProperty("repairPlanId").orEmpty().ifBlank { null }, p.getProperty("repairTarget").orEmpty().ifBlank { null },
        p.getProperty("repairReplacementSha256").orEmpty().ifBlank { null }, p.getProperty("lastMessage"),
        p.getProperty("eventCount").toInt(), p.getProperty("journalHeadSha256"))

    private fun readCase(file: File): ProfessionalWorkflowCase? = runCatching {
        require(!java.nio.file.Files.isSymbolicLink(file.toPath()))
        require(file.canonicalFile.parentFile == casesDir.canonicalFile)
        val p = Properties().apply { file.inputStream().use { load(it) } }
        val value = caseFromProperties(p)
        require(verifyJournal(value)) { "Diário de auditoria corrompido; caso bloqueado." }
        value
    }.getOrNull()

    private fun verifyJournal(c: ProfessionalWorkflowCase): Boolean = runCatching {
        val decoder = Base64.getDecoder()
        val rows = journal.readLines(Charsets.UTF_8).filter { it.startsWith(c.id + "|") }
        if (rows.size != c.eventCount) return false
        var previous = "0".repeat(64)
        rows.forEachIndexed { index, row ->
            val parts = row.split('|')
            if (parts.size != 7 || parts[0] != c.id || parts[1].toInt() != index + 1 || parts[4] != previous) return false
            val event = String(decoder.decode(parts[2]), Charsets.UTF_8)
            val detail = String(decoder.decode(parts[3]), Charsets.UTF_8)
            val expected = sha256(listOf(c.id, index + 1, event, detail, previous, parts[5]).joinToString("|"))
            if (expected != parts[6]) return false
            previous = expected
            if (index == rows.lastIndex && parts[5] != snapshotHash(c)) return false
        }
        previous == c.journalHeadSha256
    }.getOrDefault(false)

    private fun snapshotHash(c: ProfessionalWorkflowCase): String = sha256(listOf(
        c.id, c.phase.name, c.buildReference, c.sourceName, c.workspacePrefix, c.sourceSha256,
        c.assessmentSha256.orEmpty(), c.verificationId.orEmpty(), c.verificationStatus.orEmpty(),
        c.binaryArtifactSha256.orEmpty(), c.repairPlanId.orEmpty(), c.repairTarget.orEmpty(),
        c.repairReplacementSha256.orEmpty(), c.lastMessage, c.eventCount
    ).joinToString("|"))

    private fun validWorkspacePrefix(value: String): Boolean = value.isNotBlank() && value.length <= 180 &&
        !value.startsWith('/') && !value.contains('\\') && value.split('/').all { it.isNotBlank() && it != "." && it != ".." }

    private fun sha256(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
