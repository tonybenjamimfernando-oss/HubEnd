# AR-01 — Seguranca do ciclo de vida da ponte nativa

Registo de decisao do plano AR-1. Cada afirmacao abaixo tem evidencia verificavel no historico
do Git e no CI deste repositorio.

## 1. Baseline (AR-00)

- HEAD de referencia: `86ec01f` (ramo `main`), CI verde nos 4 jobs.
- Ponte nativa: `android/app/src/main/java/dev/hubend/app/bridge/HubEndEnginesPlugin.kt`.
- Contrato de dados: `BridgePayloads` (CONTRACT_VERSION = 1), testes JVM em `app/src/test`.
- APK publicado: v0.1.3, debug, 32,4 MB, SHA-256 verificado contra as notas da release.

## 2. Defeito identificado (hipoteses descartadas e causa suportada)

Observacao no codigo da ponte, antes da correcao:

1. `respond()` entregava o resultado ao `PluginCall` a partir da thread do executor
   sem verificar se a ponte ainda estava viva. Depois de `handleOnDestroy()`, essa entrega
   atravessa a WebView destruida (caminho `JsReplyProxy.postMessage`).
2. `executor.shutdownNow()` descartava as tarefas em fila sem as resolver ou rejeitar:
   as promessas JavaScript correspondentes ficavam suspensas para sempre.
3. `handleOnDestroy()` nao era idempotente nem deixava estado observavel do fecho.
4. `openNativeConsole()` respondia na thread de interface sem verificar o estado da ponte.

Isto e uma causa suportada por leitura direta do codigo. A reproducao em dispositivo continua
sujeita a evidencia de CI (ver seccao 5).

## 3. Alteracao minima

- Novo `BridgeLifecycleGate` (Kotlin puro, sem dependencias Android) com estados
  `ACTIVE -> CLOSING -> DESTROYED`, transicoes idempotentes e registo de chamadas pendentes.
- `HubEndEnginesPlugin` passa a:
  - recusar chamadas novas com `UNAVAILABLE` quando a ponte esta em fecho;
  - entregar resultados apenas em `ACTIVE` (callbacks tardios sao descartados);
  - rejeitar as chamadas pendentes com `UNAVAILABLE` antes de desligar o executor;
  - tornar `handleOnDestroy()` idempotente e sem propagacao de excecoes.
- Nenhuma operacao exposta foi removida, nenhum campo do contrato foi alterado.
- Nenhuma permissao nova; o terminal e os trabalhos Termux continuam fora da WebView.

## 4. Testes adicionados

`android/app/src/test/java/dev/hubend/app/bridge/BridgeLifecycleGateTest.kt` (JVM, corre em
`:app:testDebugUnitTest` no CI):

- aceita chamadas enquanto ativa;
- rejeita chamadas novas apos `beginClosing()`;
- entrega resultado apenas enquanto ativa;
- fecho idempotente devolve pendentes uma unica vez;
- excecao numa entrega tardia nao propaga;
- `seal()` descarta pendentes.

## 5. Evidencia e limites

- Evidencia desta alteracao: execucao de CI do PR associado (jobs `Testes, lint e APK debug`,
  `Testes em emulador Android` e `Projetos gerados compilam`).
- O teste de teardown em emulador ja existente no ramo `diag/teardown-race-isolation`
  (10 iteracoes x 3 cenarios) correu verde e deve ser mantido como regressao de risco.
- Limite explicito: os testes JVM provam a guarda e a sua semantica, nao provam o
  comportamento de todas as versoes de WebView. A matriz de dispositivos continua a ser
  necessaria antes de encerrar P0 em definitivo.

## 6. Rollback

Reverter esta alteracao e repor o commit anterior em `HubEndEnginesPlugin.kt` e remover
`BridgeLifecycleGate.kt` e o respetivo teste. Nao ha migracao de dados nem alteracao de esquema,
pelo que o rollback e seguro e imediato.

## 7. Efeito no HIP

A guarda acrescenta os estados de execucao exigidos ao contrato (pedido aceite, em execucao,
concluido, rejeitado) e o codigo de erro `UNAVAILABLE`. O adaptador de compatibilidade mantem
`status`, `ledger` e `openNativeConsole` com as mesmas entradas e saidas em caso de sucesso.