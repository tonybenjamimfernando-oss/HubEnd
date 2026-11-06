# Validação HubOps 1.16.0 — Espelhar-X Repair Rollback

## Alterações
- Adicionado rollback explícito para propostas de reparação aplicadas.
- A reversão valida por SHA-256 que o ficheiro ainda corresponde exatamente ao conteúdo proposto.
- A reversão valida que o backup corresponde ao hash original registado na proposta.
- O conteúdo original é restaurado por ficheiro temporário e movimento atómico quando suportado.
- O backup é arquivado após a reversão; não é apagado deliberadamente.
- A interface apresenta as reparações aplicadas durante a sessão e disponibiliza a reversão controlada.

## Verificações executadas nesta sessão
- `kotlinc` compilou isoladamente `EspelharProjectServices.kt` com o harness de verificação.
- Smoke test executável passou: aplicar reparação, validar backup, reverter, arquivar backup, aplicar novamente e recusar reversão após alteração posterior do ficheiro.
- Os testes JUnit foram acrescentados para rollback bem-sucedido, edição posterior e backup adulterado; não foram executados via Gradle.

## Não validado
- Compilação Android integral e compilação Compose/ViewModel.
- `testDebugUnitTest` e `assembleDebug`.
- Teste em dispositivo Android.
- Persistência da lista de reparações aplicadas após encerramento da app: a lista é mantida em memória nesta versão, embora os backups arquivados permaneçam no workspace.

## Critério de aceitação
A reversão só é permitida quando o conteúdo atual e o backup original correspondem aos hashes esperados. Se o utilizador ou outro processo alterar o ficheiro após a reparação, a operação é recusada para preservar alterações posteriores.
