# Validação 1.20.0 — Recuperação Transacional

## Implementado
- Motor independente `TransactionRecoveryEngine` com diário persistente por transação.
- Estados: `PREPARED`, `APPLYING`, `VALIDATING`, `COMMITTING`, `COMMITTED`, `ROLLING_BACK`, `ROLLED_BACK`, `NEEDS_ATTENTION`.
- Escrita temporária e substituição atómica quando suportada; checksum SHA-256 para detetar alteração/corrupção do registo.
- Deteção de operações não terminais após reinício. A deteção apenas atualiza o diário; não modifica pastas de projeto.
- Integração no fluxo de transferência ZIP do `HubOpsOrchestrator`.
- Testes unitários para interrupção/reexecução, operação concluída, traversal, transição inválida e checksum adulterado.

## Limites de segurança
- `NEEDS_ATTENTION` exige reconciliação explícita; não há promoção, remoção de staging ou rollback automático.
- O motor regista caminhos relativos e valida traversal, mas a execução efetiva de rollback/limpeza continua sob responsabilidade do motor proprietário e deve validar novamente caminhos canónicos e hashes no momento da ação.
- O diário é protegido por checksum contra corrupção acidental, não por assinatura criptográfica contra um atacante com capacidade de reescrever o ficheiro e recalcular o hash.

## Validação executada
- Revisão estática das transições e pontos de integração.
- A execução de Gradle/JUnit e build Android depende de Gradle/Android SDK disponíveis; não declarar compilação integral sem executar esses passos.
