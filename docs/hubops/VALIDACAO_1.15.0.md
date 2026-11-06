# Validação HubOps 1.15.0 — Espelhar-X Snapshot Diff & Graph Accuracy

## Alterações desta versão
- GitHub Bridge: comparação somente-leitura entre o snapshot remoto e a pasta local `github-owner-repo`.
- Classificação de ficheiros: novos, alterados, iguais e apenas locais.
- Comparação não escreve, remove, importa nem substitui ficheiros; a importação permanece numa ação separada.
- Limites: máximo 200 ficheiros, 500 KB por ficheiro remoto e 10 MB totais; validação de caminhos, destino canónico e rejeição de links locais que escapem do workspace.
- Project Intelligence: imports de classes passam a associar apenas a declaração com nome e package correspondentes. Imports wildcard recebem estado distinto `package-associated`.
- Build preflight: a normalização dos caminhos não remove mais pontos iniciais de segmentos.
- Versão Android: 1.15.0 (versionCode 22).

## Verificações executadas
- `kotlinc` compilou isoladamente `EspelharProjectServices.kt`.
- Smoke test JVM passou: associação exata do import sem falsos positivos de package; classificação de snapshot diff; confirmação de que a comparação não altera ficheiros; reparação com backup; rejeição de hash obsoleto; rejeição de traversal.
- Os testes JUnit correspondentes foram acrescentados ao projeto, mas não executados via Gradle nesta sessão.

## Não validado nesta sessão
- Compilação Android integral e compilação da interface/ViewModel com Android Gradle Plugin.
- Execução de `testDebugUnitTest` e `assembleDebug`.
- Gradle Wrapper: o projeto contém `gradle-wrapper.properties`, mas não contém o script `gradlew` nem `gradle-wrapper.jar`.
- Instalação de Android SDK/Build Tools no ambiente.
- Carregamento JNI e grammars Tree-sitter num dispositivo Android.
- Chamada online autenticada ao GitHub; a comparação foi testada com snapshots locais simulados.

## Critério de aceitação
O diff GitHub é uma comparação estática entre o snapshot obtido da API e a cópia local já importada. Não é um Git diff de commits, não descarrega histórico completo e não faz merge/push. A validação definitiva do app requer restaurar o Gradle Wrapper oficial, executar testes e build Android, e testar Tree-sitter num dispositivo.
