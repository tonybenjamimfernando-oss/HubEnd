# HubOps 1.17.0 — Arquitetura de dois motores autónomos

## Princípio

- **Despertar** mantém a responsabilidade de conceber, gerar, preparar e construir aplicações.
- **Espelhar-X** mantém a responsabilidade de importar, inspecionar, comparar, diagnosticar e transformar projetos existentes.
- **Core partilhado** contém contratos e infraestrutura neutra, não lógica específica de um dos motores.
- **HubOpsOrchestrator** coordena transferências, proveniência, impacto, autorização, validação e histórico.

Nenhum motor passa a depender da implementação interna do outro para executar as suas funções individuais.

## Infraestrutura partilhada nesta versão

`core/code/TreeSitterAstAnalyzer.kt` contém o analisador AST e os modelos `AstProjectReport`, `AstFileResult` e `AstDeclaration`. Despertar (`ProjectIntelligence`) e Espelhar-X (`EspelharEngine.pipeline`) usam a mesma implementação. A indisponibilidade de grammar/JNI continua explicitamente reportada.

`core/integration/HubOpsOrchestrator.kt` contém:

- Identidade de motor (`EngineId`).
- Proveniência do artefacto: motor de origem, nome, SHA-256 e timestamp.
- Impacto: destino, contagem de fontes, ignorados, colisões, volume expandido e riscos.
- Proposta de transferência com estados persistentes e cópia privada do ZIP de origem, para permitir retomar a aprovação sem depender do cache temporário do Despertar.
- Gate de validação e aceitação.
- Integration Ledger append-only e Project Registry.

## Fluxo de transferência Despertar → Espelhar-X

1. **Prepare:** inspeciona ZIP sem extrair, valida entradas, calcula hash, conta ficheiros, estima impacto e verifica colisões. Não escreve no workspace final.
2. **Approve:** o utilizador confirma explicitamente a proposta apresentada.
3. **Stage:** fontes reconhecidas são importadas para `integration-staging/<proposal-id>`; a importação recusa sobrescritas.
4. **Validate:** o pipeline do Espelhar-X analisa exclusivamente a pasta de staging. A evidência inclui contagem, erros, avisos, estado AST e SHA-256 do relatório resumido.
5. **Promote:** apenas evidência aceite permite mover os ficheiros para o destino final. Colisões são novamente verificadas; movimentos parciais são revertidos.
6. **Register:** origem e evidência são gravadas numa única entrada `ACCEPTED` do Integration Ledger; o Project Registry é uma projeção derivada desses eventos, evitando duas gravações divergentes.

A validação bloqueia a promoção se houver erros, ficheiros esperados não analisados, erros sintáticos AST, grammar/JNI indisponível, truncagem ou omissões.

## Fluxo inverso Espelhar-X → Despertar

O Espelhar-X exporta uma pasta para um ZIP temporário somente-leitura. O Despertar analisa o snapshot através de `ProjectIntelligence`, que usa a AST partilhada. O hash do snapshot e do relatório é registado como `READ_ONLY_INSPECTION`. Este fluxo não altera o projeto original.

## Estados de transferência

- `PREPARED`: impacto calculado; aguarda aprovação.
- `IMPORTED_TO_STAGING`: conteúdo isolado, ainda não aceite.
- `VALIDATION_BLOCKED`: evidência insuficiente ou com falhas; não promovido.
- `ACCEPTED`: validação aprovada, promoção concluída e registo criado.
- `FAILED`: falha operacional registada; não equivale a aceitação.

## Limitações atuais

- O fluxo mutável governado implementado nesta versão é Despertar → Espelhar-X. O sentido Espelhar-X → Despertar é análise de snapshot somente-leitura, não uma fusão mutável.
- O gate usa análise estática/AST; não substitui compilação Android, testes de execução, revisão de segurança nem testes de regressão.
- Se Tree-sitter/JNI não estiver disponível para uma linguagem suportada, a promoção é bloqueada. O projeto fica isolado em staging para investigação.
- A validação Android integral e a execução JUnit via Gradle ainda requerem Gradle/Android SDK.
