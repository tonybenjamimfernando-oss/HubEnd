# Validação 1.23.0 — Autorreparação controlada

## Implementado
- Motor independente `ControlledRepairEngine`, com preparação, aplicação aprovada e reversão condicionada.
- Planos vinculados a SHA-256 do conteúdo atual esperado e do ficheiro de substituição.
- Aprovação explícita por token retornado no plano; o token não é aceite se o plano persistido divergir.
- Backup preservado e verificado antes da substituição; promoção por movimento atómico quando suportado.
- Revalidação de hash imediatamente antes e depois da aplicação.
- Reversão apenas se o destino ainda corresponder ao hash aplicado e o backup corresponder ao hash original.
- Caminhos relativos, proteção contra traversal e rejeição de links simbólicos.
- Ficheiros criados pelo reparador não são apagados automaticamente numa reversão.
- Integração via `HubOpsOrchestrator.prepareControlledRepair`, `approveAndApplyControlledRepair` e `rollbackControlledRepair`.

## Limites de segurança
- Operação restrita a um ficheiro por plano; não altera diretórios inteiros nem permissões.
- A aprovação é uma confirmação explícita da camada chamadora, não uma autenticação criptográfica de utilizador.
- O motor não resolve corridas externas de filesystem entre verificação e escrita em todos os sistemas; para workspaces partilhados, a aplicação deve ser serializada e o workspace não deve ser modificado concorrentemente.
- Em rollback de ficheiro originalmente inexistente, o motor bloqueia a remoção e exige revisão manual.

## Validação efetiva
- Compilação isolada do novo motor com `kotlinc`: aprovada.
- Gradle/JUnit completo e build Android: não executados neste ambiente (Gradle/Android SDK não disponíveis).
- Smoke test executável com `kotlinc`: aprovado para preparação, aprovação, aplicação, backup, reversão, rejeição de destino alterado e rejeição de traversal.
- Testes JUnit adicionados para aprovação, backup/reversão, destino alterado, traversal e bloqueio de remoção automática; execução Gradle pendente.
- Testes em dispositivo real: pendentes.
