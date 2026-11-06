# HubOps 1.11.0 — reabilitação do Espelhar-X

## Escopo
A revisão incidiu no motor Espelhar-X, no seu ecrã e na ponte de receção de projetos gerados pelo Despertar. Os módulos Despertar, GitHub, WordPress, Feed, Terminal e respetivas configurações funcionais foram preservados.

## Melhorias implementadas
- Cache SHA-256 LRU limitado a 128 análises e sincronizado.
- Validação de caminhos relativos antes da análise, leitura, gravação e importação.
- Scanner de delimitadores ignora strings multilinha/triplas Python e Kotlin, strings normais e comentários tratados anteriormente.
- Parser JSON exige que não exista conteúdo residual depois do valor principal.
- Pipeline ordena os caminhos recolhidos e mantém os limites de 200 ficheiros, 500 KB por ficheiro e 10 MB por execução.
- Importação do ZIP do projeto gerado no Despertar, no diretório `projects/despertar`, com validação de traversal, limite de 30 MB comprimidos, 1.000 entradas, 200 ficheiros suportados, 500 KB por ficheiro e 20 MB expandidos. A importação prepara e valida os ficheiros antes de gravar e rejeita colisões em vez de sobrescrever.
- A interface oferece a importação apenas quando existe um projeto gerado pelo Despertar.

## Limitações mantidas
- A análise permanece heurística; não equivale a compilação nem a AST completa para todas as linguagens.
- A importação reconhece apenas extensões suportadas pelo Espelhar-X e não importa binários, dependências ou artefactos de build.
- A importação não faz fusão semântica nem altera o projeto original; disponibiliza cópias de fontes para análise/edição dentro do workspace Espelhar-X.
- A execução Shell continua explícita e usa o UID Android da aplicação; não é proot, contentor ou sandbox forte.

## Validação realizada
- `EspelharEngine.kt` compilado isoladamente com `kotlinc` e stub temporário de `JSONTokener` para contornar a ausência do SDK Android no ambiente.
- Testes JUnit acrescentados para traversal, JSON com conteúdo residual, strings triplas, importação ZIP sem sobrescrita e Zip Slip. A execução Gradle dos testes ainda depende de ambiente com Gradle/Android SDK.
- Build Android/Compose integral e testes em dispositivo físico não foram executados nesta sessão.
