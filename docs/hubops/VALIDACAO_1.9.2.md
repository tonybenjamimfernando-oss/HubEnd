# HubOps 1.9.2 — reparação e conclusão da fase geral

## Âmbito

Esta versão fecha uma ronda de reparação dos módulos gerais da aplicação: GitHub, WordPress, Terminal, HTTP, EventBus e Feed. Os ficheiros sob `app/src/main/java/dev/hubops/despertar/` não foram intencionalmente alterados.

## Reparações confirmadas por inspeção

- A interface GitHub invocava `HubViewModel.myRepos()` sem que o método existisse; implementado o método e o endpoint autenticado `/user/repos`.
- A interface Terminal invocava `HubViewModel.clearTerminalHistory()` sem que o método existisse; implementada a limpeza do histórico em memória da sessão.
- As operações novas de leitura GitHub validam `owner/repository`, limitam o tipo de recurso a branches, commits, releases e issues e excluem pull requests da lista de issues.
- WordPress passou a consultar páginas e categorias, além de publicações. Páginas são somente leitura nesta versão; a escrita continua limitada à criação de rascunhos de publicações.
- Eventos de páginas são integrados no feed WordPress. O EventBus tem buffer finito e política `DROP_OLDEST` para não bloquear produtores.
- Feed/registo de atividade podem ser limpos pelo utilizador; listas de recursos GitHub e categorias WordPress têm paginação na interface.
- O feed limpa o erro anterior quando uma operação Terminal termina com sucesso e permite limpar feed/registo.
- As credenciais rejeitam caracteres de controlo e nomes WordPress com separador Basic Auth ambíguo; categorias descodificam entidades HTML e posts sem ID positivo são descartados.

## Verificações executadas

- Compilação Kotlin isolada dos módulos gerais: HTTP, modelos/adapters, GitHub, WordPress, Terminal, EventBus e FeedAggregator — aprovada com stubs temporários para Android/JSON/configuração e compatibilidade da API de coroutines disponível no ambiente.
- 19 testes focados executados com runner JUnit-compatível local: validação de URL WordPress, validação GitHub, política HTTPS, execução de comandos, código de saída, timeout e limites de saída — 19 aprovados.
- Smoke test separado do EventBus/FeedAggregator: evento de páginas, ocultação do comando Terminal no feed e limpeza do feed — aprovado.
- Referências de métodos `vm.*()` usadas pelos ecrãs Compose comparadas com os métodos públicos do `HubViewModel`: nenhuma referência em falta.
- XML do Manifest: válido. ZIP final: `unzip -t` sem erros.

## Não executado / limitações

- `assembleDebug` e `testDebugUnitTest` oficiais não foram executados: não há Gradle/Wrapper funcional nem Android SDK configurado neste ambiente.
- A compilação isolada não substitui a compilação Compose/Android nem valida a ligação JNI das grammars Tree-sitter.
- Não foram efetuadas chamadas reais autenticadas ao GitHub ou WordPress, nem escritas em contas externas.
- O Terminal continua a executar comandos arbitrários fornecidos pelo utilizador sob o UID da aplicação; não é um contentor nem um substituto do Termux.
- A listagem GitHub depende dos scopes/permissões do token. Páginas WordPress privadas dependem da Application Password e das permissões da conta.
- A versão não adiciona escrita a branches/issues/releases nem publicação WordPress; não há mutações remotas além da criação de rascunho já existente.

## Versão

- `versionCode`: 16
- `versionName`: 1.9.2
