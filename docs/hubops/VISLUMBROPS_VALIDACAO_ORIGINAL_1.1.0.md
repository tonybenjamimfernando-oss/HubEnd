# Estado de validação — 1.1.0

## Inspeção e alterações confirmadas

- ZIP de entrada lido e inventariado: 31 entradas; estrutura ZIP sem erro de CRC (`testzip`).
- Projeto Android não continha `settings.gradle.kts`, build de raiz, `gradle.properties` nem `app/proguard-rules.pro`; estes ficheiros foram criados.
- O teste original estava fora do source set Gradle (`test/kotlin`); foi movido para `app/src/test/java`.
- Foram encontrados muitos `\$` antes de interpolação Kotlin; corrigidos para que os valores sejam efetivamente apresentados.
- A checagem anterior de assinatura confundia ficheiros `META-INF` com validação de assinatura APK v2/v3; agora a verificação criptográfica é explicitamente `SKIPPED`.
- A análise de permissões por busca de strings no XML binário foi substituída por `PackageManager`.
- O fluxo ADB podia instalar, executar e desinstalar um APK no dispositivo ligado; esse comportamento foi removido do adaptador padrão.

## Testes adicionados

- Inventário e hashes por entrada.
- Hash do manifesto.
- Identificação de DEX e bibliotecas nativas.
- Rejeição de caminho `../` no ZIP.
- Recusa de arquivo acima do limite.
- Testes existentes de cadeia de evidências, certificado, adulteração de metadados do certificado e entropia.
- Teste de escape/parse JSON com nomes de entradas que contêm aspas e quebras de linha.
- A interface passou a oferecer importação SAF e exportação do manifesto JSON por SAF.

## Não executado neste ambiente

- Não há Gradle nem Kotlin compiler disponíveis no ambiente desta execução.
- Não foi executado `gradle test`, `assembleDebug` ou `assembleRelease`.
- Não foi compilado APK, nem instalado em dispositivo.
- Os testes JUnit foram escritos/movidos, mas não executados.
- PackageManager, Android Keystore, SAF, Compose e serviço Android precisam de validação em Android Studio/emulador.
- A validação de assinatura APK v1-v4 e o laboratório de runtime continuam por integrar.

## Critério honesto

O código e a estrutura foram alterados e inspecionados; isto não equivale a uma compilação Android bem-sucedida. A versão só deve ser considerada pronta para distribuição depois de Gradle sync, testes, build e ensaio num dispositivo Android.
