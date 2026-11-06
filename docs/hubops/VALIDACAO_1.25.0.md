# Validação 1.25.0 — Fluxo profissional Pro

## Entregue

- Painel Compose `Pro Flow` ligado ao router principal.
- Exportação de snapshot do workspace Espelhar-X e análise limitada com `ProjectIntelligence`/AST.
- Auditoria de APK reutilizando o `VerificationViewModel` do VislumbOps.
- Consolidação via `CrossEngineAssessmentCoordinator`.
- Preparação e aplicação de correções de ficheiro único via `ControlledRepairEngine`.
- Aprovação explícita por diálogo; token de aprovação não é copiado para o diário do fluxo; o motor de reparação mantém o token no seu registo privado para validar a aprovação.
- Backup, revalidação de hashes, reversão condicionada e nova auditoria após reconstrução do APK.
- `ProfessionalWorkflowCoordinator`: persistência do caso, transições validadas, diário append-only encadeado por SHA-256 e bloqueio de retoma se o diário divergir.
- Retoma de caso: origem deve corresponder ao fingerprint determinístico de caminhos e conteúdos persistido; plano pendente sem o ficheiro de correção original é invalidado e exige nova preparação.

## Validação executada neste ambiente

- Compilação isolada Kotlin do `ProfessionalWorkflowCoordinator`: aprovada.
- Smoke test executável do ciclo completo (origem → avaliação → preparação → aplicação → reauditoria): aprovado.
- Sete testes do coordenador executados num harness Kotlin isolado: 7/7 aprovados (ciclo, revisão, reversão, adulteração do diário/snapshot, reutilização do APK e referência inválida).
- Verificação de persistência do caso e integridade da cadeia de eventos: aprovada.
- Confirmação de que o token de aprovação não é copiado para o diário Pro: aprovada; o registo privado do ControlledRepairEngine conserva-o para validação.
- Build Gradle/JUnit oficial não executado; o harness isolado não substitui o pipeline Android completo.

## Limitações / por validar

- Não existe `gradle` instalado nem wrapper `gradlew` no pacote de entrada; Android SDK não foi confirmado. Build Android e JUnit completo não executados.
- Testes de UI Compose e fluxo real com APK e workspace de utilizador não executados neste ambiente.
- O projeto precisa estar no workspace privado do Espelhar-X; o destino deve existir dentro do mesmo prefixo do projeto analisado.
- O HubOps não constrói o APK por si neste fluxo: após aplicar a correção, o projeto deve ser reconstruído num ambiente confiável e o novo snapshot do workspace e APK selecionados.
- A referência de build é fornecida pelo utilizador. Não prova relação criptográfica entre snapshot e APK; isso requer atestação de build assinada.
- A cadeia SHA-256 é evidência de integridade local, não assinatura digital contra um atacante com controlo total do armazenamento privado.

## Segurança operacional

- A auditoria AST e APK é somente-leitura.
- Nenhuma correção é inferida ou aplicada automaticamente.
- A aprovação é explícita e o motor revalida o hash do destino antes de aplicar.
- Backups são mantidos pelo motor de reparação; reversão permanece condicionada à integridade do destino e backup.
- Casos com diário adulterado não são retomados.
