# Validação técnica — HubOps 1.8.2

Data: 2026-11-05

## Alterações verificadas

- Motor `CodeSimilarityEngine.kt` compilado isoladamente com `kotlinc`.
- 13 métodos de teste de `CodeSimilarityEngineTest.kt` compilados e executados num runner local com API mínima compatível com as anotações/assertions JUnit usadas pelos testes.
- Cenários cobertos: correspondência exata, diferenças de identificadores/literais, ficheiros movidos, prevenção de emparelhamento de ficheiros triviais, ordem das instruções, comentários aninhados Kotlin, strings multilinha, divisão inteira Python, ficheiros sem par, caminhos duplicados após normalização e relatório de limites.
- ZIP final verificado estruturalmente e checksum SHA-256 calculado após empacotamento.

## Limitações de validação

- Não foi executado `assembleDebug` nem `testDebugUnitTest` do Gradle: este ambiente não tem Gradle instalado, Android SDK configurado, `gradlew` nem `gradle-wrapper.jar` no projeto recebido.
- A execução dos 13 testes não substitui a execução com a dependência JUnit real dentro do Gradle; foi usada uma implementação mínima local apenas para executar os métodos e assertions presentes neste ficheiro de teste.
- A aplicação Android integral e a interface Compose não foram compiladas neste ambiente.
- Não existe ainda parser AST real nem resolução de símbolos para Python, Kotlin ou Java. A refatoração continua desativada, deliberadamente, para não produzir alterações inseguras.

## Critérios de aceitação para a etapa AST

1. Parser versionado e compatível com cada linguagem suportada.
2. Resolução de símbolos e escopos antes de qualquer renomeação.
3. Plano dry-run com diff e lista completa de referências afetadas.
4. Transformação numa cópia separada, nunca sobre o original.
5. Reparse sem erros após transformação.
6. Compilação/testes de regressão aprovados antes de permitir exportação.
