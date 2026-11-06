# Validação — HubOps 1.12.0

## Alterações

1. O pipeline do Espelhar-X alimenta agora o `TreeSitterAstAnalyzer` com fontes Kotlin (`.kt`/`.kts`), Java (`.java`) e Python (`.py`).
2. A interface apresenta o relatório AST, declarações com caminho/linha, indisponibilidades de parser e limites de processamento.
3. A gravação usa ficheiro temporário e `ATOMIC_MOVE` quando o sistema de ficheiros suporta; caso contrário usa substituição por movimento normal.
4. A análise continua estática: nenhum código é executado durante o pipeline.

## Limites explícitos

- Tree-sitter nesta versão não faz resolução semântica de tipos, referências, overloads, reflexão ou comportamento em runtime.
- O analisador AST existente suporta Kotlin, Kotlin DSL, Java e Python; outras extensões continuam na análise heurística disponível.
- A presença das dependências no Gradle não comprova que grammars/JNI carreguem em todos os dispositivos. A interface reporta parser indisponível quando isso ocorre.
- O build Android e a suíte JUnit não foram executados neste ambiente: não há Gradle instalado nem `gradle-wrapper.jar` no ZIP recebido.

## Verificações executadas neste ambiente

- Inspeção da estrutura do ZIP e dos pontos de integração do ViewModel/UI.
- Verificação textual das alterações e das referências ao relatório AST.
- Confirmação de que os ficheiros de Despertar e os restantes serviços não foram editados nesta etapa.

## Próxima validação necessária

Executar `./gradlew testDebugUnitTest assembleDebug` num ambiente com JDK 17, Android SDK 34 e wrapper Gradle completo; depois validar no dispositivo o carregamento JNI das quatro grammars e os resultados AST de projetos reais.
