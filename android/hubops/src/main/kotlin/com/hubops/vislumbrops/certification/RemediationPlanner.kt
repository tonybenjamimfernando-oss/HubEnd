package com.hubops.vislumbrops.certification

import com.hubops.vislumbrops.core.*

class RemediationPlanner {

    fun plan(contract: VerificationContract): RemediationPlan {
        val target = chooseTarget(contract)
        val steps = contract.findings
            .sortedByDescending { it.severity.ordinal }
            .mapIndexed { idx, f ->
                RemediationStep(
                    order = idx + 1,
                    action = actionFor(f),
                    addressesFindingIds = listOf(f.id),
                    estimatedEffort = effortFor(f)
                )
            }
        return RemediationPlan(targetEngine = target, steps = steps)
    }

    private fun chooseTarget(c: VerificationContract): Engine {
        val hasStatic = c.findings.any {
            it.category in setOf(
                CheckCategory.SECRETS,
                CheckCategory.PERMISSIONS,
                CheckCategory.STATIC_ANALYSIS,
                CheckCategory.SUPPLY_CHAIN,
                CheckCategory.ARTIFACT_INTEGRITY
            )
        }
        val hasRuntime = c.findings.any { it.category == CheckCategory.RUNTIME_STABILITY }
        return when {
            hasRuntime && !hasStatic -> Engine.DESPERTAR
            hasStatic -> Engine.ESPELHAR_X
            else -> Engine.HUBOPS
        }
    }

    private fun actionFor(f: Finding): String = when (f.category) {
        CheckCategory.SECRETS ->
            "Remover segredo em ${f.location ?: "artefacto"}; usar Keystore."
        CheckCategory.PERMISSIONS ->
            "Rever permissão ${f.title.removePrefix("Permissão sensível: ")}"
        CheckCategory.DEPENDENCIES ->
            "Atualizar dependência: ${f.description.take(120)}"
        CheckCategory.SUPPLY_CHAIN ->
            "Rever cadeia de fornecimento e inventário do APK: ${f.description.take(120)}"
        CheckCategory.ARTIFACT_INTEGRITY ->
            "Validar integridade, estrutura e assinatura do artefacto: ${f.description.take(120)}"
        CheckCategory.RUNTIME_STABILITY ->
            "Corrigir: ${f.description.take(120)}"
        CheckCategory.PERFORMANCE ->
            "Otimizar: ${f.description.take(120)}"
        else -> "Rever: ${f.title}"
    }

    private fun effortFor(f: Finding): String = when (f.severity) {
        Severity.CRITICAL -> "imediato (<24h)"
        Severity.HIGH -> "curto prazo (<1 semana)"
        Severity.MEDIUM -> "médio prazo (<1 mês)"
        else -> "backlog"
    }
}
