# Validação HubOps 1.14.0 — Espelhar-X

## Alterações
- Adicionada pré-verificação de build Android ao pipeline Espelhar-X.
- A pré-verificação inspeciona manifesto, build raiz, settings, build do módulo app, fontes e os três artefactos do Gradle Wrapper.
- O resultado é classificado como `blocked`, `review` ou `preflight-ready` e nunca afirma que houve compilação.
- O ecrã apresenta bloqueios e avisos, incluindo ausência de `gradle-wrapper.jar`.
- Acrescentados testes para grafo de dependências, reparação com backup e hash, rejeição de traversal, pré-verificação, importação GitHub sem sobrescrita e análise de conflitos Component Fusion.
- Versão Android: 1.14.0 (versionCode 21).

## Verificações executadas neste ambiente
- `kotlinc` compilou isoladamente `EspelharProjectServices.kt`.
- `kotlinc` compilou conjuntamente `TreeSitterAstAnalyzer.kt`, `EspelharProjectServices.kt` e `EspelharEngine.kt` usando um stub local de `org.json.JSONTokener` apenas para satisfazer a dependência ausente no ambiente. Isto valida sintaxe/compatibilidade Kotlin desses três ficheiros, não substitui a compilação Android nem valida o comportamento real do parser JSON.
- Smoke test JVM executado com sucesso: associação de imports locais, aplicação de reparação com backup, rejeição de reaplicação com hash obsoleto e pré-verificação de build.

## Ainda não validado
- Build Android integral e execução JUnit via Gradle: não há Gradle instalado nem `gradle-wrapper.jar` no projeto/ambiente.
- Carregamento JNI/grammars Tree-sitter em Android real.
- Execução real de Gradle/SDK e geração de APK.
- Chamada autenticada/online ao GitHub nesta sessão.
- Fusão automática de código: deliberadamente não implementada; Component Fusion continua a comparar, detetar conflitos e produzir plano, sem copiar código automaticamente.

## Critério de aceitação
A pré-verificação é apenas um diagnóstico estático. `preflight-ready` significa que os ficheiros básicos esperados foram encontrados, não que o projeto compila. A conclusão do ciclo exige `./gradlew testDebugUnitTest`, `./gradlew assembleDebug` e testes num dispositivo Android com Tree-sitter disponível.
