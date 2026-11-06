# Validação técnica — HubOps 1.17.0

## Confirmado neste ambiente

1. `HubOpsOrchestrator.kt` compilado isoladamente com `kotlinc`.
2. `TreeSitterAstAnalyzer.kt` compilado isoladamente com `kotlinc` depois da sua mudança para `dev.hubops.core.code`.
3. `ProjectIntelligence.kt`, `ProjectSymbolIndex.kt` e a AST partilhada compilados conjuntamente.
4. `EspelharEngine.kt`, `EspelharProjectServices.kt` e a AST partilhada compilados conjuntamente com um stub temporário de `org.json.JSONTokener`. Isto valida sintaxe e integração Kotlin destes ficheiros, mas não o comportamento real do JSONTokener Android.
5. O teste JUnit novo do orquestrador foi compilado com stubs temporários de JUnit para validar sintaxe; não foi executado pelo runner JUnit.
6. Smoke test de integração real em JVM: ZIP → proposta → staging → pipeline scoped → promoção → Project Registry/Integration Ledger.
7. Smoke test de segurança: colisão no destino bloqueada, artefacto alterado após a proposta recusado e caminho `../` em ZIP rejeitado.

8. Smoke test do fluxo inverso: exportação ZIP do Espelhar-X preservou os ficheiros originais.
9. Teste de fail-closed AST: sem grammar/JNI disponível, a fonte Kotlin foi marcada como indisponível, permitindo bloquear a promoção.
10. Smoke test de segurança de caminhos: links simbólicos foram recusados tanto na análise scoped como no descarte de staging, preservando o destino original.

## Não confirmado

- Build Android/Compose e compilação de `HubViewModel`/ecrãs: não executados; Gradle e Android SDK não estão instalados neste ambiente.
- JUnit integral: testes adicionados, mas não executados via Gradle.
- Tree-sitter JNI/grammars em dispositivo Android: não executado.
- Build real do projeto transferido: a integração faz análise estática/AST; não declara que o Android Gradle Plugin compilou o projeto.

## Critério de aceitação aplicado

Uma transferência só é promovida se a contagem analisada cobrir todos os ficheiros importados, não existirem erros, a AST cobrir todas as fontes Kotlin/Kotlin DSL/Java/Python, não existirem erros sintáticos, gramáticas indisponíveis, truncagem ou ficheiros omitidos. Avisos são mantidos na evidência e não são ocultados.
