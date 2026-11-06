# Validação 1.22.0 — Planeamento Assistido de Recuperação

## Implementado
- Novo `RecoveryActionPlanner`, componente puro e independente que transforma cada resultado de reconciliação num plano consultivo específico.
- Ações diferenciadas para operação concluída, staging disponível, artefacto ausente/alterado, transação órfã, divergência entre ledger/destino e revisão manual.
- Cada ação informa a razão e se exige aprovação humana e/ou nova validação.
- Hash SHA-256 determinístico do plano para identificar a proposta de recuperação apresentada.
- `HubOpsOrchestrator.planRecovery(stagingWorkspace)` liga reconciliação e planeamento sem conceder capacidade de execução.
- Testes unitários para todos os estados, ausência de execução automática, staging com aprovação/revalidação e determinismo.

## Segurança
- `automaticExecutionAllowed` é sempre `false`.
- O planeador não recebe diretórios nem callbacks de escrita e não altera diário, staging, artefactos, destino ou ledger.
- A existência de um plano não constitui aprovação nem substitui validação atualizada do conteúdo.

## Validação efetiva
- Compilação isolada de `RecoveryActionPlanner.kt` com `kotlinc`: aprovada.
- Smoke test executável para os seis estados de reconciliação: aprovado; todos produziram plano não executável e hash SHA-256 de 64 caracteres.
- Revisão estática da integração com o orquestrador.
- A execução JUnit/Gradle e a compilação Android não foram realizadas neste ambiente; exigem Gradle e Android SDK compatíveis.
- Testes no dispositivo e validação de fluxos reais de recuperação permanecem pendentes.
