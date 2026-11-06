package dev.hubops.core.integration

import dev.hubops.core.recovery.RecoveryState
import java.security.MessageDigest

/** Suggested, non-executable recovery workflow. Every mutating step requires a fresh approval. */
enum class RecoveryActionKind {
    NO_ACTION, INSPECT_STAGING, REVALIDATE_STAGING, RESTORE_SOURCE_ARTIFACT,
    VERIFY_DESTINATION_AND_LEDGER, MANUAL_FORENSICS, BLOCK_AUTOMATIC_RECOVERY
}

data class RecoveryAction(
    val kind: RecoveryActionKind,
    val title: String,
    val rationale: String,
    val requiresHumanApproval: Boolean,
    val requiresFreshValidation: Boolean,
)

data class RecoveryActionPlan(
    val transactionId: String,
    val proposalId: String?,
    val reconciliationStatus: ReconciliationStatus,
    val actions: List<RecoveryAction>,
    val automaticExecutionAllowed: Boolean,
    val planSha256: String,
)

/** Pure planner: reads reconciliation evidence and emits recommendations; it never touches files. */
object RecoveryActionPlanner {
    fun plan(result: RecoveryReconciliation): RecoveryActionPlan {
        val actions = when (result.status) {
            ReconciliationStatus.CONSISTENT_COMMITTED -> listOf(
                action(RecoveryActionKind.NO_ACTION, "Manter operação concluída", "Diário, ledger e destino estão presentes.")
            )
            ReconciliationStatus.STAGING_AVAILABLE_FOR_REVIEW -> listOf(
                action(RecoveryActionKind.INSPECT_STAGING, "Inspecionar staging", "Comparar os ficheiros staged com a proposta e o impacto aprovado.", approval = true),
                action(RecoveryActionKind.REVALIDATE_STAGING, "Revalidar antes de qualquer promoção", "Executar novamente validação estática, AST e integridade sobre o staging atual.", approval = true, validation = true),
                action(RecoveryActionKind.BLOCK_AUTOMATIC_RECOVERY, "Bloquear promoção automática", "A presença do staging não prova que o conteúdo é seguro ou completo.")
            )
            ReconciliationStatus.SOURCE_ARTIFACT_MISSING_OR_CHANGED -> listOf(
                action(RecoveryActionKind.RESTORE_SOURCE_ARTIFACT, "Localizar novamente o artefacto original", "Exigir correspondência exata com o SHA-256 registado; não aceitar substitutos.", approval = true, validation = true),
                action(RecoveryActionKind.MANUAL_FORENSICS, "Investigar divergência de proveniência", "Preservar evidências e explicar a ausência ou alteração do artefacto."),
                action(RecoveryActionKind.BLOCK_AUTOMATIC_RECOVERY, "Bloquear recuperação automática", "A origem não pode ser considerada confiável enquanto o hash não corresponder.")
            )
            ReconciliationStatus.ORPHANED_TRANSACTION -> listOf(
                action(RecoveryActionKind.MANUAL_FORENSICS, "Investigar transação órfã", "Identificar proposta e origem por evidências externas verificáveis."),
                action(RecoveryActionKind.BLOCK_AUTOMATIC_RECOVERY, "Não reconstruir a proposta por suposição", "Faltam ligações confiáveis entre diário e proposta.")
            )
            ReconciliationStatus.LEDGER_DESTINATION_MISMATCH -> listOf(
                action(RecoveryActionKind.VERIFY_DESTINATION_AND_LEDGER, "Verificar destino e ledger", "Comparar conteúdo real, hashes por ficheiro e registo de aceitação.", approval = true, validation = true),
                action(RecoveryActionKind.MANUAL_FORENSICS, "Preservar e investigar divergências", "Não presumir que o destino existente corresponde à transferência concluída."),
                action(RecoveryActionKind.BLOCK_AUTOMATIC_RECOVERY, "Bloquear nova promoção", "Evitar duplicação ou sobrescrita enquanto o estado final for incerto.")
            )
            ReconciliationStatus.NEEDS_MANUAL_REVIEW -> listOf(
                action(RecoveryActionKind.MANUAL_FORENSICS, "Recolher evidências adicionais", "Inspecionar diário, workspace, staging e logs sem alterar os originais."),
                action(RecoveryActionKind.BLOCK_AUTOMATIC_RECOVERY, "Manter operação suspensa", "Evidência insuficiente para escolher uma recuperação segura.")
            )
        }
        val canonical = buildString {
            append(result.transactionId).append('|').append(result.proposalId).append('|').append(result.status.name)
            actions.forEach { append('|').append(it.kind.name).append(':').append(it.requiresHumanApproval).append(':').append(it.requiresFreshValidation) }
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return RecoveryActionPlan(result.transactionId, result.proposalId, result.status, actions,
            automaticExecutionAllowed = false, planSha256 = hash)
    }

    private fun action(
        kind: RecoveryActionKind,
        title: String,
        rationale: String,
        approval: Boolean = false,
        validation: Boolean = false,
    ) = RecoveryAction(kind, title, rationale, approval, validation)
}
