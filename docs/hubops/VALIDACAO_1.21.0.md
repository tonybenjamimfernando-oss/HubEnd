# Validação 1.21.0 — Reconciliação Transacional

## Objetivo concluído nesta fase
Fechar o ciclo de recuperação transacional com uma camada de reconciliação que compara evidências persistidas sem executar ações destrutivas.

## Implementado
- `HubOpsOrchestrator.reconcileInterruptedTransactions(stagingWorkspace)` cruza diário de recuperação, proposta persistida, artefacto privado, SHA-256, diretório de staging, destino e ledger de aceitação.
- Classificações: `CONSISTENT_COMMITTED`, `STAGING_AVAILABLE_FOR_REVIEW`, `SOURCE_ARTIFACT_MISSING_OR_CHANGED`, `ORPHANED_TRANSACTION`, `LEDGER_DESTINATION_MISMATCH` e `NEEDS_MANUAL_REVIEW`.
- Transações concluídas podem ser reconciliadas usando o registo de aceitação mesmo depois da proposta e do artefacto privado terem sido removidos.
- A reconciliação é estritamente read-only. Não cria, apaga, move, promove ou reverte ficheiros.
- Teste de integração cobre operação bloqueada na validação, persistência do staging e confirmação de que a reconciliação não altera o conteúdo.

## Limites e segurança
- O chamador deve fornecer a raiz real do workspace Espelhar-X; os caminhos derivados são canonicalizados e limitados a essa raiz.
- A classificação é evidencial, não uma autorização automática de recuperação. `STAGING_AVAILABLE_FOR_REVIEW` exige inspeção/decisão humana e nova validação antes de qualquer promoção.
- A existência do destino não prova por si só a integridade de todos os seus ficheiros; a fase não executa varredura recursiva nem compara conteúdo final.
- O checksum do diário deteta corrupção acidental, mas não é assinatura autenticada contra um atacante com acesso de escrita.

## Validação executada
- Revisão estática dos caminhos de reconciliação e dos limites canonicalizados.
- Compilação Kotlin isolada do motor de recuperação e do orquestrador com `kotlinc` (registar resultado efetivo na entrega).
- Gradle/Android SDK e execução JUnit completa não disponíveis neste ambiente; build Android e teste em dispositivo permanecem pendentes.
