# Validação 1.24.0 — avaliação cruzada AST + VislumbOps

## Implementado
- `CrossEngineAssessmentCoordinator` recebe um ZIP-fonte, uma referência de build explícita e um `VerificationContract` VislumbOps concluído.
- Calcula e revalida SHA-256 do ZIP-fonte; exige SHA-256 válido no contrato binário.
- Mantém hashes distintos para fonte e APK; não afirma que o APK foi compilado a partir do ZIP apenas pela referência fornecida.
- Agrega findings VislumbOps, erros sintáticos AST e alertas heurísticos do ProjectIntelligence.
- Reporta cobertura AST, ficheiros indisponíveis/omitidos/truncados, limitações e hash SHA-256 da evidência agregada.
- Exposto por `HubOpsOrchestrator.assessProjectAcrossEngines`.
- Fluxo somente de leitura; não altera projetos, APKs, staging, ledger ou certificados.

## Testes adicionados
- Junção de achados AST e VislumbOps com proveniência de artefactos separada.
- Rejeição de contrato VislumbOps ainda em execução.
- Rejeição de referência de build inválida e hash binário inválido.

## Limitações
- A referência de build é fornecida pelo chamador e não prova a relação de compilação. Uma futura etapa pode verificar atestação de build/SBOM assinada.
- Tree-sitter não substitui compiladores, testes, análise semântica ou execução.
- A agregação mantém as limitações dos scanners e do contrato VislumbOps de origem.

## Estado de validação
- Revisão estática dos pontos de integração: concluída.
- Gradle/JUnit e build Android: não executados neste ambiente (não há comando `gradle` disponível).
- Testes no dispositivo Android: pendentes.
