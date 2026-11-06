# HubEnd — Entrega A: Auditoria e arquitetura

Data: 6 nov 2026 · Fontes: `HubOps-VislumbOps-Integrated-1_29_0.zip`, `MVP-HybridApp-codigo-fonte.zip`,
repositório `tonybenjamimfernando-oss/HubEnd` (vazio à data da inspeção).

## 1. Inventário

### HubOps 1.29.0 (projeto principal, fonte de verdade)

| Item | Valor |
|---|---|
| Linguagem / UI | Kotlin 2.2.21, Jetpack Compose (Material 3) |
| Build | AGP 8.10.1, Gradle 8.11.1 (sem wrapper no ZIP), compileSdk/targetSdk 34, minSdk 26, JDK 17 |
| Ficheiros | 186 (≈ 12 000 linhas Kotlin em `app/src/main`) |
| Testes | 27 ficheiros JUnit 4 (171 testes) |
| Ferramentas | `tools/gopa` (servidor Go), `tools/snse` (treino/quantização/assinatura de modelos), `tools/nsc` (PRoot/rootfs), `tools/quality` |
| Documentação | 40 ficheiros em `docs/` (validações 1.8.2 → 1.29.0) |
| Estado declarado | `VALIDACAO_1.29.0.md`: Gradle, JUnit e APK **não executados** |

Motores (ver estado por capacidade em `src/lib/engines/catalog.ts` e na área **Motores** da app):

| Motor | Localização | Função |
|---|---|---|
| HubOps Core | `dev/hubops/core` | Registo de motores, orquestrador de transferências em duas fases, reparação controlada, recuperação, ledger, AST Tree-sitter, HTTP, configuração cifrada |
| Espelhar-X | `dev/hubops/espelhar` | IDE offline, workspace, grafo de dependências, comparação, reparações |
| Despertar | `dev/hubops/despertar` | Estudo de repositórios, gerador de projetos, similaridade, fusão, diagnóstico de build, Termux (JADX/APKTool/Gradle) |
| VislumbOps | `com/hubops/vislumbrops` | Auditoria de APK, cadeia de fornecimento, permissões, segredos, CVEs, evidências, atestação local |
| SNSE | `dev/hubops/omega/ai` | Roteamento simbólico por CWE + arbitragem; runtime llama.cpp plugável |
| NSC | `dev/hubops/omega/containers` | Planeamento de execução em camadas, PRoot/Alpine |
| GoPA | `dev/hubops/omega/merkle` + `tools/gopa` | Atestações Ed25519, Merkle, quórum |
| Serviços | `dev/hubops/data` | GitHub, WordPress, Terminal (UID da app), Dispositivo |
| Feed | `dev/hubops/glue` | Agregador de eventos |

### HybridApp MVP (base tecnológica)

| Item | Valor |
|---|---|
| Stack real | **React 19 + TanStack Start + Vite + Tailwind 4 + Capacitor 7** (o prompt indica Vue 3 + Capacitor 6; seguiu-se o código real) |
| Ficheiros | 131 |
| Núcleo reutilizável | `lib/core/eventBus.ts`, `apiClient.ts` (retry só 5xx/rede, cache LRU), `store.ts`, `database.ts` |
| Nativo | `lib/native/*` (SQLite, biometria, push, segundo plano) com alternativa no navegador |
| Android | Capacitor gerado no CI (`cap add android`), APK carrega `APP_URL` |
| Testes | 25 testes Vitest |

## 2. Matriz de integração

| Componente | Origem | Função | Decisão |
|---|---|---|---|
| Motores HubOps (Core, Espelhar-X, Despertar, VislumbOps, Omega) | HubOps | Funcionalidade principal | **Integrar** — módulo `:hubops`, sem remoções |
| Interface Compose HubOps | HubOps | Consola nativa completa | **Integrar** — atividade nativa aberta pela ponte |
| Testes HubOps | HubOps | Validação | **Integrar** — `:hubops:testDebugUnitTest` |
| GoPA / SNSE / NSC tools | HubOps | Servidor e pipelines | **Integrar** — `tools/`, caminhos atualizados |
| Shell Capacitor | HybridApp | Infraestrutura híbrida | **Adaptar** — projeto `android/` versionado (não gerado no CI) |
| EventBus, ApiClient, store | HybridApp | Serviços da interface | **Integrar** — `src/lib/core` |
| EventBus Kotlin vs TS | Ambos | Comunicação | **Unificar** — um por camada, ligados pela ponte |
| Cliente GitHub Kotlin vs TS | Ambos | Integração | **Unificar** — motores usam Kotlin; interface só leitura pública |
| Terminal | Ambos | Execução | **Adaptar** — só na consola nativa (segurança) |
| Base CVE | Ambos | Auditoria | **Unificar** — `res/raw/cve_database.json` |
| AGP 8.10.1/Java 17 vs AGP 8.7.2/Java 21 | Ambos | Build | **Adaptar** — AGP 8.10.1, Java 21, compileSdk 36 |
| minSdk 26 vs 23 | Ambos | Compatibilidade | **Adaptar** — minSdk 26 |
| FileProvider duplicado | Ambos | Partilha | **Unificar** — um provider |
| SQLite, biometria, push | HybridApp | Serviços nativos | **Planear** — Fase 1 do shell |
| llama.cpp/JNI, PRoot, Billing | HubOps | Runtimes, pagos | **Planear** — Fases 1, 3, 4 |

Nenhum módulo foi eliminado.

## 3. Problemas encontrados e correções

Primeira compilação real do HubOps 1.29.0 (nesta integração):

| # | Problema | Ficheiro | Correção |
|---|---|---|---|
| 1 | `Process.pid()` não existe em Android (erro de compilação) | `omega/ai/AndroidLlamaCliRuntime.kt`, `omega/containers/NestedSandboxCascade.kt` | `omega/ProcessIds.kt`: PID por reflexão; **sem PID a execução é recusada** (falha fechada) |
| 2 | Literais `'\\n'`, `'\\r'`, `'\\t'`, `'\\b'`, `'\\u000c'` inválidos | `vislumbrops/analysis/ArtifactSupplyChainEngine.kt:68-72` | Literais corretos `'\n'`… |
| 3 | `Button` fora de `item {}` num `LazyColumn` | `ui/screens/EspelharScreen.kt:124-128` | Envolvidos em `item {}` |
| 4 | Gramáticas Tree-sitter exigem compileSdk ≥ 36 | metadados AAR | compileSdk 36 (targetSdk 35) |
| 5 | `'a'.repeat(64)` (Char) no teste | `test/.../DespertarTest.kt:236` | `"a".repeat(64)` |

Execução dos 171 testes HubOps (4 falhas reais, corrigidas):

| Teste | Causa | Correção |
|---|---|---|
| `BuildRepairAdvisorTest.successfulEmptyLogIsNotClaimedAsVerifiedBuild` | Registo vazio com exit 0 devolvia lista vazia | Devolve achado INFO a dizer que não há evidência do build |
| `ControlledRepairEngineTest.refusesTraversalAndDoesNotAutoDeleteNewFilesOnRollback` | Recusa lançava `IllegalStateException` | `IllegalArgumentException`, como as restantes recusas |
| `DespertarTest.androidBuildJobValidates…` | Teste esperava `apksigner verify` sem a aspa do caminho citado | Teste corrigido (o script estava certo) |
| `ComponentFusion…` (2 testes) | Remoção da pasta raiz apagava `src/` dos caminhos | Só remove pastas envolventes, não `src`, `app`, `java`, `kotlin`, `res`, `lib(s)` |

Execução em Android real (emulador Android 11, API 30) — defeitos invisíveis na JVM, corrigidos em 0.1.1:

| # | Problema | Ficheiro | Correção |
|---|---|---|---|
| 6 | **APK 0.1.0 fechava ao abrir**: `PatternSyntaxException` no arranque — `}` sem escape na regra `checkServerTrusted…\{\s*}`; a JVM aceita, o ICU do Android rejeita | `omega/ai/SnseEngine.kt:144` (`SymbolicPatchArbiter`) | `\\}` |
| 7 | **Consola nativa fechava no Feed**: o `%` da bateria era interpolado no padrão de `format()` (`UnknownFormatConversionException`) | `ui/screens/FeedScreen.kt:37` | `%%` |
| 8 | Teste lia `src/main/res/raw/cve_database.json` do disco (só existe no computador de build) | `test/.../CveCatalogTest.kt` | Em ART lê o recurso empacotado `R.raw.cve_database` |
| 9 | APKs de teste com chaves diferentes não instalavam por cima | `app/build.gradle` | Chave de depuração fixa `app/hubend-debug.keystore` (só testes) |

Os testes HubOps passaram a correr também em ART (`:hubops:connectedDebugAndroidTest -PhubendDeviceTests`)
e o módulo app tem testes de arranque e de navegação pelos 10 ecrãs da consola (`app/src/androidTest`).

Riscos mantidos e documentados:

- **Desbloqueio pago do Despertar** é um interruptor local (`TermuxBridge.kt:279-285`) → Fase 4 (Google Play Billing + validação no servidor).
- **Ledger local** sem assinatura por linha → planeado encadeamento SHA-256 + GoPA.
- **PRoot** não é isolamento de kernel.
- **SNSE neural** não operacional sem runtime e modelos GGUF assinados.
- **Base CVE** com 11 entradas.

## 4. Arquitetura HubEnd

```text
┌──────────────────────────── APK dev.hubend.app ─────────────────────────────┐
│  MainActivity (Capacitor BridgeActivity)                                    │
│   └─ WebView ── Interface HubEnd (React/TanStack, src/)                      │
│        │          APP_URL publicado  ·  ou ecrã local android-shell/          │
│        │                                                                     │
│        └─ Plugin HubEndEngines (app/.../bridge) ── contrato JSON v1          │
│              status · ledger (só leitura) · openNativeConsole               │
│                         │                                                    │
│  :hubops (biblioteca) ──┴─ HubOpsApplication · HubOpsEngineRegistry          │
│     HubOps Core ⇄ Despertar ⇄ Espelhar-X   VislumbOps → Core (evidência)     │
│     Omega: SNSE · NSC · GoPA          Consola nativa Compose (9 ecrãs)       │
└─────────────────────────────────────────────────────────────────────────────┘
```

- **Dois motores autónomos de mutação** (Despertar, Espelhar-X) + **inteligência partilhada**
  (VislumbOps, SNSE, AST Tree-sitter) + **orquestração comum** (HubOpsOrchestrator + registo de fluxos).
- Nenhum motor aceita alterações de outro sem proveniência (SHA-256 do artefacto), impacto
  (colisões, ficheiros, bytes), aprovação explícita e evidência de validação.
- A ponte **não** expõe shell, Termux nem escrita no ledger.

### Estrutura do repositório

```text
android/            Gradle raiz (wrapper 8.11.1)
  app/              Shell Capacitor + ponte HubEndEngines + testes do contrato
  hubops/           HubOps 1.29.0 (motores, UI Compose, 171 testes)
android-shell/      Ecrã local do APK (sem APP_URL)
src/                Interface HubEnd (rotas, componentes, serviços partilhados, ponte TS)
tools/              GoPA (Go), SNSE, NSC, qualidade
docs/               Auditoria, validação, documentação HubOps e HybridApp, marca
.github/workflows/  CI Android com proveniência
```

## 5. Fases

| Fase | Estado |
|---|---|
| A — Auditoria | Concluída (este documento) |
| 0 — APK funcional | Testes e APK debug compilados localmente; falta instalação/navegação num dispositivo real e CI verde no GitHub |
| 1 — llama.cpp/JNI | Pendente |
| 2 — SNSE neural | Pendente (depende da Fase 1) |
| 3 — Termux/PRoot | Pendente |
| 4 — Billing | Pendente |
