# Changelog

## 1.29.0 — Dependency auditor repair and offline quality gates

- Fixed `DependencyAuditor` version matching: numeric half-open ranges `[introduced, fixed)` replace textual prefixes (false positives such as 4.10 vs "4.1", false negatives such as log4j 2.1–2.13).
- New JVM-pure `CveCatalog`/`VersionComparator`/`VersionRange`; invalid database entries are rejected and counted, never interpreted permissively.
- CVE database rebuilt with ranges (11 entries); corrected Heartbleed range; removed unverified OkHttp CVE-2023-0833 mapping.
- Versions now read from `META-INF/maven/*/*/pom.properties` when present; INFO findings deduplicated; a clean scan reports PASS (not WARN) while stating the base is non-exhaustive.
- SecretScanner: OpenSSH/DSA/encrypted PEM keys, GitHub fine-grained PAT, Slack webhook, Stripe restricted key, Telegram bot token, Anthropic API key, Mailgun key.
- Manifest now references the existing `network_security_config`.
- Added `tools/quality/*` checks, wired into CI; added `CveCatalogTest` (9 tests, not executed here: no Gradle).
- README version/Alpine inconsistencies fixed.
- Not validated: Gradle build, JUnit run, device tests (see docs/VALIDACAO_1.29.0.md).

## 1.28.0 — Omega runtime preparation and protocol alignment

- SNSE: added host-side LoRA training with a prompt-mask-preserving collator, GHSA commit-diff extraction, strict held-out exact-output evaluator, GGUF manifest and per-model Ed25519 signatures.
- SNSE: added Android llama.cpp CLI runtime adapter with serialized execution, bounded output and timeout; added signed native-binary/model installation paths. Seven GGUFs are distributed separately from the APK to avoid multi-gigabyte APK packaging.
- NSC: added signed release manifest, explicit Alpine 3.24.4 bootstrap, bounded transactional TAR extraction and safe in-rootfs symlink handling; PRoot remains an ELF executable, with dynamic libraries staged separately.
- GoPA: replaced the incompatible HMAC sketch with the Android GoPA Ed25519 canonical protocol, Bouncy Castle provider for API 26+, fixed eligible-peer quorum, immutable per-peer receipts, HTTPS/TLS, transport token, durable JSON ledger and gossip validation.
- Omega UI now exposes signed llama.cpp installation, per-shard GGUF import and explicit PRoot/Alpine bootstrap; release public key is embedded from the build environment.
- Added Go unit tests for 7-of-10 quorum, non-shrinking quorum, signature tampering, duplicate receipts and persistence.
- Not yet validated: Gradle Android build, NDK builds, actual PRoot/Alpine boot on device, llama-cli execution on Android, seven-model fine-tuning, held-out precision/recall, device RAM/latency/thermal measurements.

## 1.27.0 — Omega: SNSE, NSC e GoPA

- Integra `SnseEngine`: router simbólico/CWE (catálogo offline limitado), até dois shards por confiança, gestor de modelos GGUF com orçamento conservador, runtime substituível e arbiter com limiares ponderados por severidade/CWE e filtros de patches para revisão humana.
- Integra `NestedSandboxCascade`: planeamento explícito JVM estático / PRoot user-space / Termux delegado / indisponível; PRoot exige binário ELF e rootfs privados, aprovação, timeout e monitorização best-effort de RSS.
- Integra `GopaConsensus`: receipts Ed25519, compromisso Merkle de hashes de evidência, peer directory explícito, deduplicação por peer/artefacto e quórum `floor(2M/3)+1` sobre peers elegíveis.
- `OmegaEngineSuite` é inicializado no ciclo de vida da aplicação; rota Omega adicionada à navegação; registry anuncia as capacidades.
- Não foram incluídos pesos 1B, runtime llama.cpp, proot, rootfs nem transporte gossip. Estes motores comunicam limitações e usam fallback seguro; não se anunciam benchmarks de RAM/latência nem isolamento equivalente a Docker.
- Compilações Kotlin isoladas e 14/14 métodos de teste Omega aprovados em harness JVM isolado (shim JUnit; não Gradle oficial). Gradle, JUnit oficial, lint, APK e teste Android real continuam pendentes.


## 1.26.0 — Recuperação consistente do diário e CI com proveniência

- Introduz commit write-ahead para o diário e snapshot do Pro Flow, com recuperação após interrupção antes/depois do append e sem replay duplicado.
- Reforça a avaliação cruzada: cobertura AST incompleta e ausência de atestação assinada deixam de poder resultar numa avaliação aprovada.
- Corrige os imports do contrato VislumbOps para o package real `com.hubops.vislumbrops.core`.
- Adiciona GitHub Actions para testes JVM, lint Android, APK debug, SHA-256 e atestação de proveniência.
- Build Android oficial e execução do workflow remoto ainda pendentes neste ambiente sem Android SDK e sem DNS para transferir dependências.


## 1.25.0 — Fluxo profissional Pro e trilho de auditoria integrado

- Novo painel Pro Flow no router principal: snapshot do workspace Espelhar-X → AST/diagnóstico → auditoria APK VislumbOps → avaliação cruzada → plano de correção → aprovação explícita → reparação controlada → nova auditoria.
- O diagnóstico e a reparação ficam vinculados ao mesmo prefixo de workspace; apenas ficheiros existentes dentro do projeto analisado podem ser alterados.
- Fingerprint de fonte determinístico calculado sobre caminhos e conteúdos, ignorando timestamps e ordem das entradas ZIP; nova auditoria exige conteúdo-fonte e APK com hashes diferentes, além de referência de build nova.
- Novo `ProfessionalWorkflowCoordinator` com casos persistidos, transições de fase controladas, diário append-only encadeado por SHA-256 e validação de integridade antes de retomar um caso.
- Integração do fluxo no `HubViewModel`, incluindo exportação limitada do snapshot do workspace e importação da correção, avaliação cruzada, aplicação via `ControlledRepairEngine` e registo de reauditoria.
- Recuperação após reinício: origem anterior exige correspondência exata de SHA-256; plano preparado sem o ficheiro de correção é invalidado e tem de ser preparado novamente.
- A nova auditoria exige snapshot atualizado do mesmo workspace e APK reconstruído; a referência de build continua declarativa e não constitui atestação criptográfica de proveniência.
- Reversão da última correção exposta no painel Pro, condicionada à integridade do destino e do backup.
- Testes adicionados para ciclo completo, revisão obrigatória, invalidação de plano, reversão e adulteração do diário/snapshot.
- Limitação: build Gradle/Android e execução JUnit completa ainda dependem de ambiente Android SDK/JDK/Gradle disponível.


## 1.24.0
- Integração de avaliação cruzada entre AST do Espelhar-X/Despertar e contrato de auditoria VislumbOps concluído.
- Proveniência separada por SHA-256 para ZIP-fonte e APK, ligada por referência de build explícita.
- Normalização de achados sintáticos/heurísticos, métricas de cobertura AST, limitações e hash de evidência agregada.
- Operação somente de leitura; sem promoção, reparação ou certificação automática.


## 1.23.0
- Added `ControlledRepairEngine` for explicitly approved, single-file repairs within a selected workspace.
- Binds each repair plan to the target's expected SHA-256 and replacement SHA-256; rejects stale targets and changed replacement files.
- Preserves and verifies backups before replacement; uses atomic move where supported.
- Supports guarded rollback only when the applied target and backup still match their recorded hashes.
- Refuses path traversal and symbolic links; never auto-deletes a newly created file during rollback.
- Exposed prepare/apply/rollback methods through `HubOpsOrchestrator`.


## [1.22.0] - 2026-11-06

### Added
- `RecoveryActionPlanner`: converte resultados da reconciliação em planos consultivos específicos por cenário.
- Planos incluem ações, justificações, requisitos de aprovação humana e revalidação, com hash SHA-256 determinístico.
- `HubOpsOrchestrator.planRecovery()` expõe os planos após reconciliação read-only.
- Testes para todos os estados, bloqueio de execução automática, revalidação do staging e determinismo do hash.

### Safety
- Os planos não executam ações, não alteram ficheiros e nunca autorizam promoção automática.
- Operações sem evidência suficiente permanecem bloqueadas para revisão humana.


## [1.21.0] - 2026-11-06

### Added
- Reconciliação read-only das transações: cruza diário, proposta persistida, SHA-256 do artefacto, staging, destino e ledger de aceitação.
- Classificação explícita de operações consistentes, staging disponível, artefacto ausente/alterado, transações órfãs e divergências de ledger/destino.
- Teste de integração para recuperação interrompida, preservação de staging e ausência de mutações durante reconciliação.

### Safety
- A reconciliação não promove, elimina, renomeia nem reverte ficheiros; decisões de recuperação continuam explícitas.


## 1.20.0 — Motor de Recuperação Transacional
- Introduzido diário persistente por operação, com estados explícitos e checksum SHA-256.
- Recuperação após reinício identifica operações não terminais e coloca-as em `NEEDS_ATTENTION`; não apaga, substitui, promove nem reverte ficheiros automaticamente.
- Orquestrador regista início, aplicação, validação, promoção e conclusão de transferências ZIP.
- Expostos métodos para inspecionar operações interrompidas e consultar transações recentes.
- Testes adicionados para reinício/reexecução idempotente, transições inválidas, traversal e corrupção do diário.


## 1.19.0
- Adicionado o motor independente `ApkSignatureVerificationEngine`.
- Verificação de assinatura delegada ao parser de pacotes Android, com extração dos certificados e fingerprints SHA-256.
- Metadados de package/signatários adicionados ao contrato de auditoria e ao relay do HubOps.
- Removido o check genérico SKIPPED que apenas anunciava a ausência de verificação criptográfica.
- Falhas de parsing ou ausência de certificados são reportadas como FAIL/ERROR; não são aprovadas silenciosamente.
- Mantida a distinção entre verificação feita pelo Android e validação especializada dos esquemas APK v1/v2/v3/v4.

- Incorporado o motor VislumbOps 1.1.0, respetiva interface de auditoria, relatórios, certificação, análise estática, cadeia de fornecimento e testes.
- Consolidada a aplicação Android: HubOps continua a ser a única aplicação, com uma única Application, Activity launcher e navegação; VislumbOps permanece um motor autónomo dentro dela.
- Criado `HubOpsEngineRegistry` com política explícita de fluxos e `HubOpsVerificationRelay`, que valida a cadeia de evidências/atestação e publica o resultado no ledger comum com hashes e estado de não-mutação.
- Adicionados testes do ledger, registry de motores e integração externa.
- Adicionadas dependências Kotlin Serialization e Lifecycle Runtime Compose; versão Android 1.18.0 (versionCode 25).
- Validação Android/Gradle ainda pendente; nenhum APK foi declarado como compilado.

## 1.17.0 — HubOps Dual-Engine Orchestration

### Arquitetura partilhada
- `TreeSitterAstAnalyzer` e os modelos AST foram movidos para `core/code`, deixando de pertencer ao namespace Despertar. Despertar e Espelhar-X consomem agora a mesma infraestrutura de inteligência de código, mantendo os seus motores e fluxos autónomos.
- Adicionado `HubOpsOrchestrator`: propostas de transferência com origem, SHA-256, destino, impacto, colisões, limites e riscos explícitos.
- Transferência Despertar → Espelhar-X passou a ser bifásica: preparação sem escrita; confirmação explícita; importação em staging; análise limitada ao staging; promoção apenas após validação; registo persistente.
- Propostas pendentes persistem em ficheiros Properties atómicos e podem ser recuperadas após reinício da app; o ZIP de origem é copiado para armazenamento privado e verificado por hash.
- Adicionados `Integration Ledger` e `Project Registry` persistentes com proveniência e hashes de evidência.
- A validação bloqueia a promoção quando há erros, falhas AST, gramáticas indisponíveis, truncagem, ficheiros omitidos ou contagem incompleta.
- Adicionada exportação somente-leitura de projeto do Espelhar-X para análise no Despertar, com hash de origem/relatório e registo de auditoria.
- Adicionada promoção de staging por rename de diretório, com deteção de colisões, rejeição de links simbólicos e retorno ao staging se a verificação/inscrição final falhar.

### Validação executada
- `kotlinc` compilou isoladamente o orquestrador e a infraestrutura AST partilhada.
- `kotlinc` compilou `EspelharEngine`, `EspelharProjectServices` e AST partilhada com um stub temporário de `org.json.JSONTokener` para suprir a dependência Android ausente no ambiente.
- Smoke test executado: transferência bifásica, validação do staging, promoção, Project Registry e Integration Ledger.
- Smoke tests de segurança executados: colisão bloqueia escrita, artefacto alterado é recusado, ZIP traversal é rejeitado e links simbólicos não redirecionam operações de staging.
- Smoke test do fluxo inverso confirmou exportação de snapshot sem alteração dos ficheiros originais; AST indisponível foi explicitamente reportada e bloqueia aceitação.
- Build Android, compilação Compose/ViewModel, JUnit via Gradle e Tree-sitter/JNI em dispositivo continuam por validar: este ambiente não tem Gradle nem Android SDK. O stub JSON não valida o parser JSON real.
- Versão Android: 1.17.0 (versionCode 24).

## 1.15.0 — Espelhar-X Snapshot Diff & Graph Accuracy

### GitHub Bridge
- Adicionada comparação somente-leitura entre snapshot remoto e pasta local importada: novos, alterados, iguais e apenas locais.
- A comparação é apresentada antes da importação e não escreve, remove nem substitui ficheiros.
- Limites de 200 ficheiros, validação de caminhos e verificação de destino canónico.
- Mantida a importação separada, explícita e sem sobrescrita.

### Project Intelligence
- Corrigida a associação de imports: um import de classe deixa de associar incorretamente todos os ficheiros do mesmo package.
- Imports wildcard são identificados separadamente como `package-associated`.
- A pré-verificação de build deixou de remover pontos iniciais dos caminhos ao normalizar entradas.
- Acrescentados testes para falsos positivos do grafo e classificação de diferenças GitHub.

### Validação
- Compilação isolada de `EspelharProjectServices.kt` passou com Kotlin/JVM.
- Smoke tests locais para grafo, diff, reparação e proteção de caminhos em execução.
- Build Android, JUnit via Gradle, JNI/Tree-sitter em dispositivo e chamadas GitHub online continuam por validar neste ambiente.
- Versão Android: 1.15.0 (versionCode 22).

## 1.18.0 — Fusão HubOps + VislumbOps
- Incorporado o motor VislumbOps 1.1.0, respetiva interface de auditoria, relatórios, certificação, análise estática, cadeia de fornecimento e testes.
- Consolidada a aplicação Android: HubOps continua a ser a única aplicação, com uma única Application, Activity launcher e navegação; VislumbOps permanece um motor autónomo dentro dela.
- Criado `HubOpsEngineRegistry` com política explícita de fluxos e `HubOpsVerificationRelay`, que valida a cadeia de evidências/atestação e publica o resultado no ledger comum com hashes e estado de não-mutação.
- Expandido o catálogo de motores para VislumbOps e HubOps; adicionados testes do registo de verificações externas.
- Adicionadas dependências Kotlin Serialization e Lifecycle Runtime Compose; versão Android 1.18.0 (versionCode 25).
- Validação Android/Gradle ainda pendente; nenhum APK foi declarado como compilado.

## 1.13.0 — 2026-11-05

### Espelhar-X Project Intelligence
- Adicionado mapa estático de dependências e imports com estados associado/não resolvido/ambíguo.
- Adicionado Repair Engine conservador para BOM, whitespace e newline final.
- Reparações verificam SHA-256, criam backup e recusam conteúdo alterado após a análise.
- Adicionado GitHub Bridge de snapshot somente-leitura, com limites e importação sem sobrescrita.
- Pipeline e ecrã Espelhar-X passam a apresentar relações de projeto e propostas de reparação.
- Adicionados testes unitários para grafo, reparações e importação sem sobrescrita.
- Versão Android: 1.13.0 (versionCode 20).

### Validação
- Compilação isolada do serviço puro Kotlin passou.
- Build Android/Gradle, JUnit integral, Tree-sitter/JNI em Android e chamada real GitHub continuam pendentes por ausência de Gradle/SDK/rede autenticada neste ambiente. Ver `docs/VALIDACAO_1.13.0.md`.


## 1.12.0 — Espelhar-X AST Fusion

- Integração do analisador Tree-sitter existente no pipeline do Espelhar-X para Kotlin, Kotlin DSL, Java e Python.
- Relatório AST exposto na interface: ficheiros analisados/indisponíveis, erros sintáticos, nós, declarações e limites atingidos.
- Falha/ausência de grammar ou JNI é reportada como indisponibilidade; nunca é apresentada como análise AST bem-sucedida.
- Leitura dos ficheiros do pipeline reutilizada para análise heurística e AST, evitando leituras duplicadas.
- Gravação do editor reforçada com ficheiro temporário e movimento atómico quando suportado, com fallback seguro.
- Preservados os limites do workspace, a importação sem sobrescrita e a separação entre análise e execução.


## 1.11.0 — Reabilitação e integração operacional do Espelhar-X (2026-11-05)
- Cache incremental SHA-256 convertido em LRU limitado a 128 resultados e protegido contra acesso concorrente.
- Validação de caminhos relativos aplicada também à análise em memória; rejeita traversal, caminhos absolutos, segmentos ocultos e separadores ambíguos.
- Scanner de delimitadores passa a ignorar strings multilinha/triplas de Python e Kotlin, além de strings e comentários já suportados.
- Validação JSON rejeita conteúdo residual após o valor principal.
- Pipeline apresenta ordem estável e mantém limites de ficheiros/tamanho.
- Adicionada receção do ZIP gerado pelo Despertar para `projects/despertar`: até 30 MB comprimidos, 1.000 entradas, 200 fontes suportadas, 500 KB por fonte e 20 MB expandidos; valida caminhos, prepara a importação antes da escrita e nunca substitui ficheiros existentes.
- Ecrã Espelhar-X passa a expor a ponte para o projeto gerado no Despertar; análise e importação continuam separadas da execução.
- Despertar, GitHub, WordPress, Feed e Terminal não tiveram a sua lógica funcional alterada.
- Testes de regressão adicionados para traversal durante análise, JSON com lixo final, strings triplas, importação ZIP e Zip Slip.

## 1.9.1 — Aperfeiçoamento dos módulos gerais (2026-11-05)
- GitHub: paginação de pesquisa e listagem por utilizador; inclui consulta dos repositórios autorizados da conta autenticada (incluindo privados, conforme permissões do token); resultados mostram estrelas, forks, linguagem, visibilidade e data de atualização; validação de username reforçada.
- WordPress: leitura paginada de posts públicos e, quando autenticado, de conteúdos que a conta pode consultar; normalização estrita de URL HTTPS; rejeição de credenciais/query/fragment na URL base; rascunho só é confirmado quando a API devolve ID e estado `draft`.
- HTTP: adicionada variante estrita para os novos módulos GitHub/WordPress: redirecionamentos limitados e HTTPS obrigatório em cada salto; redirecionamentos de escrita bloqueados; credenciais removidas ao mudar de origem; limites de timeout e tamanho validados. O método legado usado pelo Despertar foi preservado sem alteração.
- Terminal: limites de configuração validados, stdin fechado para evitar espera invisível, captura de saída sincronizada, histórico de sessão limpável e limites de ambiente dinâmico removidos; mantém-se execução sem privilégios extra no UID da app.
- Dispositivo: diagnóstico acrescenta RAM disponível/total, estado de carregamento e tempo desde arranque; apresentação da bateria corrigida.
- Feed: replay limitado para reduzir perda de eventos durante arranque, limite global de itens e comandos anonimizados no feed/log para não expor argumentos potencialmente sensíveis.
- Parsers: melhor remoção de conteúdo script/style, descodificação de entidades HTML nomeadas e numéricas, e novos metadados de repositórios GitHub.
- Configuração: validação antes de gravar e exigência de utilizador + Application Password WordPress em conjunto.
- Despertar e os seus módulos não foram alterados nesta versão.


## 1.9.0

- Integração AST real via Tree-sitter para Kotlin/Kotlin DSL, Java e Python, com contagem de nós, declarações estruturais, linhas e erros sintáticos.
- Relatório explícito quando grammar/JNI não está disponível; não confunde análise lexical com AST.
- Code Intelligence apresenta resultados AST e mantém o índice lexical separado.
- Corrigida a deteção de Gradle Wrapper: propriedades isoladas já não são tratadas como wrapper funcional; exige script, JAR e propriedades.
- Atualizada a toolchain para AGP 8.10.1, Kotlin 2.2.21 e Gradle 8.11.1.
- Refatoração automática continua bloqueada até existir resolução semântica segura.

## 1.8.2 — 2026-11-05

- Similaridade textual e estrutural agora considera trigramas ordenados, reduzindo falsos 100% em código com operações reordenadas.
- Adicionado emparelhamento heurístico e conservador de ficheiros movidos/renomeados por linguagem e forma estrutural.
- Relatórios mostram caminho de origem/destino, tipo de emparelhamento, número de comparações e limites atingidos.
- Adicionados tratamentos para comentários de bloco aninhados em Kotlin e strings multilinha Kotlin/Java.
- Expostos no ecrã do Despertar os pares movidos e os contadores de truncagem/limites.
- Refatoração automática continua desativada até existirem parsers reais e resolução de símbolos por linguagem.


## 1.8.1 — 2026-11-05

### Code Similarity & Code Intelligence
- Corrigida a agregação de similaridade: ficheiros sem correspondência passam a reduzir a pontuação global, evitando resultados artificialmente elevados.
- Corrigida a tokenização Python: `//` é tratado como operador de divisão inteira, não como comentário.
- Literais multilinha com aspas triplas são consumidos como uma única unidade lexical (Python e blocos de texto Java/Kotlin).
- A deteção de testes passou a reconhecer diretórios `test`, `tests`, `androidTest` e `androidTests`, inclusive quando são o primeiro segmento do caminho.
- Code Intelligence passou a inventariar `.py` e `.kts`, apresentando separadamente fontes Python, Kotlin/DSL e Java.
- A remoção da pasta-raiz do ZIP deixou de remover diretórios de projeto conhecidos como `src`, `app`, `lib`, `java`, `kotlin` e `res`.
- Adicionados testes de regressão para penalização de ficheiros sem par, divisão inteira Python e strings multilinha.

### Limitações explícitas
- A comparação continua lexical/estrutural aproximada; não é uma AST produzida por compilador.
- Nenhuma renomeação ou refatoração automática é executada nesta versão.
- Não foi possível executar o build Android integral neste ambiente por ausência do Gradle/Android SDK; o motor Kotlin foi compilado isoladamente e validado por testes executáveis dedicados.

---


## 1.6.0 — Project Architect

- Adicionado Project Architect: identifica módulos Gradle, packages, imports internos/externos e dependências prováveis entre packages.
- Sinaliza dependências circulares prováveis e packages com elevada saída de dependências.
- Gera um plano de implementação em etapas e permite copiar o relatório Markdown.
- A análise é somente leitura, heurística e reutiliza os limites de leitura segura do ZIP.
- Não altera ficheiros, não executa scripts e não substitui análise AST ou compilação.

## 1.5.0 — Code Intelligence

- Adicionada análise estrutural local do ZIP Android gerado: contagem de ficheiros Kotlin/Java/testes, marcadores TODO/FIXME, ficheiros extensos e presença de manifesto/configuração Gradle/Wrapper.
- Leitura do ZIP sem extração para o armazenamento, com limites de 2.000 entradas, 1.000 ficheiros relevantes, 2 MB por entrada e 20 MB totais; caminhos absolutos e segmentos `..` são ignorados.
- Relatório apresentado no Despertar e copiável em Markdown.
- Análise heurística, sem execução de código e sem alegar que o projeto compila.
- Testes unitários adicionados para métricas, pendências, estrutura ausente e ficheiros extensos.


## 1.4.0 — Repair Engine diagnóstico (2026-11-05)

### Adicionado
- Diagnóstico determinístico de erros comuns do Gradle/Android: SDK ausente, JDK incompatível, incompatibilidade Gradle/AGP, falhas de rede/dependências, símbolos não resolvidos, classes duplicadas, Manifest Merger, memória, armazenamento, metadados Kotlin e permissões.
- Evidência limitada extraída do log, recomendação concreta e nível de confiança por diagnóstico.
- Relatório de diagnóstico copiável diretamente da interface Despertar.
- O script Builder emite `BUILD_FAILED` e `GRADLE_EXIT` quando a tarefa Gradle falha, preservando o código de saída original.
- Testes unitários para deteção de causas, limites, deduplicação e falhas sem causa identificável.

### Segurança e limites
- O Repair Engine é somente leitura: não altera automaticamente ficheiros nem aplica correções especulativas.
- Analisa no máximo os últimos 200.000 caracteres do log e apresenta até oito diagnósticos distintos.
- Uma recomendação não é prova da causa raiz; o utilizador deve rever a evidência e validar qualquer alteração.
- Não foi possível executar o build Android integral nesta sessão; a validação do motor foi feita por compilação Kotlin isolada e smoke tests.

## 2026-11-04 — Expansão profissional do Despertar
- Análise técnica de repositórios GitHub com metadados, licença, tópicos, linguagens e README limitado.
- Relatório Markdown copiável para a área de transferência.
- Classificação de licenças ampliada e testes adicionais.
- Documentação de arquitetura, limites e próximos passos.
- Mantidas as barreiras: escrita apenas no repositório autorizado do utilizador, em ramo separado; APK analysis apenas por despacho a ferramentas locais autorizadas.

## 2026-11-04 — Despertar: auditoria e execução verificável

- Auditoria de repositório ampliada com árvore Git, contagens de código/testes, CI, sistemas de build, manifestos, commits e releases.
- Relatório e interface passaram a explicitar quando a árvore está indisponível ou truncada.
- Adicionado seletor Android de APK, cópia para Downloads/HubOps, limite de 500 MB e cálculo SHA-256.
- Termux agora recebe comandos com PendingIntent de retorno, permitindo recolher exit code/stdout/stderr e mostrar o resultado na interface.
- Jobs usam allowlist de ferramentas, caminhos validados e pastas de saída não sobrescritas.
- Documentada configuração real de permissões e armazenamento Termux.
- Revisão estática heurística de até oito ficheiros do repositório, com sinais explícitos e ressalva de falsos positivos/negativos.
- Proteção contra callbacks antigos do Termux substituírem o resultado da execução atual; estado pendente, falhas de arranque e bloqueio de execuções concorrentes.
- Melhorias na validação do cabeçalho APK e criação segura da pasta de saída sem sobrescrita.

## 2026-11-04 — Despertar Performance & APK Lab
- Added bounded, streaming APK archive inspection without extracting or executing package contents.
- APK intake now verifies the AndroidManifest.xml archive entry and reports DEX, native libraries, resources, assets, and signature-file indicators alongside SHA-256.
- Added Termux environment diagnostics for Java/JADX/APKTool/aapt availability, shared-storage access, writable workspace, and free space.
- Added read-only output-folder inspection (file/folder counts, disk usage, largest files) with strict output-name validation.
- Serialized ViewModel operations with a mutex to prevent overlapping network/file/Termux actions and busy-state races.
- Added unit coverage for APK archive classification and Termux diagnostic/output command construction.
- Full Android build/device execution remains pending because this source package has no Gradle wrapper JAR or executable Gradle in the authoring environment.

## 1.1.0 — 2026-11-04

### Adicionado
- Project Forge no Despertar: geração local de projetos Android Kotlin + Jetpack Compose em ZIP.
- Validação estrita do nome e do package/applicationId antes de criar o artefacto.
- Opção explícita para incluir a permissão INTERNET no Manifest gerado.
- Partilha segura do ZIP através de AndroidX FileProvider, limitado ao diretório de cache `generated/`.
- Testes unitários para estrutura do ZIP, conteúdo do Manifest e rejeição de package inválido.

### Desempenho e segurança
- Geração executada em Dispatchers.IO.
- Não existe upload do projeto, execução automática de código gerado ou cópia de código de terceiros.
- Versionamento Android atualizado para versionCode 2 / versionName 1.1.0.

### Validação
- Compilação isolada do gerador Kotlin e smoke test de criação/inspeção ZIP aprovados.
- XML do Manifest e FileProvider validado.
- Build Android completo e testes instrumentados pendentes de ambiente Gradle + Android SDK.

### Ajustes adicionais de desempenho — 2026-11-04
- O ecrã Despertar deixou de consultar o estado Termux continuamente quando não há uma execução pendente; o polling de 1,5 s só ocorre durante trabalhos pendentes.
- A leitura inicial do resultado Termux foi movida para a inicialização do ViewModel para recuperar uma execução ainda pendente após reabrir o ecrã.
- O cache do Project Forge conserva no máximo os dois ZIPs mais recentes, removendo exportações antigas antes de criar uma nova.
- A expressão regular de identificação de ficheiros DEX passou a ser pré-compilada, evitando recompilação por entrada ZIP.


## 1.2.0 — Android Builder real via Termux (2026-11-05)

### Adicionado
- Exportação do ZIP do Project Forge para `Download/HubOps/Projects` via MediaStore, com limite de 50 MB, cópia em streaming e SHA-256; o hash é verificado novamente no Termux antes da extração, rejeitando alterações (código 45).
- Novo job Termux para extrair um projeto gerado, validar entradas ZIP contra caminhos absolutos/traversal, verificar a estrutura Gradle e compilar `:app:assembleDebug`.
- Verificação explícita de `unzip`, Java 17 e Gradle 8.7–8.9 antes da compilação.
- Limite de workers Gradle e heap de 1,5 GB para reduzir pressão de memória em dispositivos móveis.
- APK debug copiado para `Download/HubOps/Builds`, sem sobrescrever artefactos existentes.
- Interface agora oferece ação direta para compilar o projeto gerado no Termux e mostra o caminho esperado do APK.
- Testes unitários para construção do job, validação de caminhos e rejeição de identificadores inseguros.

### Limitações conhecidas
- O build é executado pelo Gradle instalado no Termux; não inclui distribuição Gradle nem Android SDK no APK HubOps.
- O ambiente Termux precisa de JDK 17, Gradle 8.7–8.9, `unzip`, SDK Android 34 e acesso de armazenamento. A primeira compilação pode precisar de rede para obter dependências e componentes SDK.
- A integração recolhe o resultado do comando Termux, mas não instala automaticamente o APK produzido.
- Não foi possível executar a compilação Android completa nesta sessão; a validação do build end-to-end no dispositivo permanece pendente.

## 2026-11-05 — Despertar Builder: validação de artefactos
- O Builder deixou de tratar a existência de `app-debug.apk` como prova suficiente de sucesso.
- Antes de publicar o APK, o Termux executa teste de integridade ZIP, confirma `AndroidManifest.xml` e DEX, compara o `applicationId` esperado com o package reportado por `aapt` e valida a assinatura com `apksigner`.
- Gera um relatório de compilação com versão Gradle, package, data UTC, tamanho, SHA-256 e resultado da verificação da assinatura.
- A interface só apresenta o caminho do APK após callback de sucesso, marcador `BUILD_OK` e caminho de saída validado; removeu-se a indicação prematura de APK apenas esperado.
- A compilação isolada da ponte Kotlin e a sintaxe do script Bash gerado foram verificadas. A compilação Android real no dispositivo continua pendente.




## 1.7.0 — Component Fusion (2026-11-05)

### Adicionado
- Comparação local, somente leitura, entre o ZIP gerado e um segundo ZIP Android escolhido pelo utilizador.
- Deteção heurística de colisões de caminhos de fontes, nomes repetidos, packages partilhados, versões divergentes de dependências Gradle e diferenças de permissões do Manifest.
- Plano de integração em etapas, com revisão de licença, resolução de conflitos, testes e validação de build.
- Importação do ZIP de referência limitada a 30 MB; análise reutiliza os limites de leitura ZIP da Code Intelligence e não extrai nem executa conteúdo.
- Testes unitários para colisões, packages, dependências, permissões e relatório.

### Limitações
- Component Fusion compara e recomenda; não copia, funde, renomeia ou altera ficheiros.
- A análise é heurística e não resolve AST, recursos Android, APIs binárias, manifest merger, NDK ou compatibilidade completa de plugins.
- Build Android integral e teste físico permanecem por validar neste ambiente.

## 1.8.0 — Code Similarity & Refactoring Lab (2026-11-05)

### Adicionado
- Medição local entre projeto principal e ZIP de referência para fontes Kotlin, Kotlin DSL, Java e Python.
- Métrica textual por tokens e métrica estrutural lexical com abstração de identificadores/literais; resultados por ficheiro e médias agregadas.
- Contagem explícita de ficheiros sem correspondência, sem os tratar como pares equivalentes.
- Interface e relatório Markdown integrados no fluxo Component Fusion, sem criar um ecrã paralelo.
- Testes para igualdade exata, abstração de identificadores/literais, deteção multilíngue e ficheiros não emparelhados.

### Segurança, precisão e limites
- Comparação somente leitura: não executa, extrai ou altera código.
- O motor é lexical/estrutural e não se apresenta como AST de compilador. A refatoração automática fica deliberadamente desativada até integrar parsers com resolução de símbolos para cada linguagem; renomear por regex ou por tokens seria inseguro.
- Limites por ficheiro e por quantidade de fontes para manter consumo de memória e latência previsíveis.
- As percentagens indicam semelhança observável, não equivalência funcional, autoria ou conformidade de licença.


## 1.8.3
- Auditoria estática passa a localizar cada sinal por ficheiro e linha.
- Deteção isolada por ficheiro elimina falsos positivos criados por concatenação de fontes distintas.
- Deteção de TLS permissivo permanece contextual e limitada ao mesmo ficheiro.
- Mantida a natureza heurística: nenhum resultado “limpo” equivale a certificação de segurança.

## 1.8.4
- Adicionado índice lexical de declarações e ocorrências para Kotlin, Kotlin DSL, Java e Python.
- Comentários e literais são excluídos da contagem; declarações duplicadas são sinalizadas como ambíguas.
- Code Intelligence mostra resumo de símbolos e inclui o índice no relatório Markdown.
- O índice é somente leitura e não autoriza renomeações: não é AST nem resolução semântica.
- Corrigida a contagem Code Intelligence para não tratar `build.gradle.kts` e `settings.gradle.kts` como ficheiros Kotlin de aplicação.


## 1.9.2 — Reparação e conclusão da fase geral (2026-11-05)

### Reparações críticas
- Corrigidas referências da interface a `HubViewModel.myRepos()` e `clearTerminalHistory()` que estavam ausentes na implementação anterior e impediam a compilação dos ecrãs GitHub/Terminal.
- `myRepos()` exige token configurado e consulta `/user/repos` com paginação; repositórios privados continuam sujeitos aos scopes/permissões do token.
- A limpeza do histórico afeta apenas a sessão atual.

### GitHub — explorador de repositórios
- Consulta paginada de branches, commits, releases e issues de um repositório.
- Paginação também disponível dentro do explorador do repositório.
- Itens de pull request são excluídos da listagem de issues para não misturar tipos.
- Validação explícita de `owner/repository` e allowlist de recursos; nenhuma operação de escrita foi adicionada.

### WordPress — gestão de conteúdo
- Consulta paginada de páginas, além de publicações.
- Consulta de categorias com nome, slug e contagem de publicações.
- Mantida criação de publicações exclusivamente como rascunho; páginas e categorias são somente leitura nesta versão.

### Fiabilidade e desempenho
- EventBus limitado com política DROP_OLDEST para não bloquear operações produtoras sob pressão do consumidor.
- O feed limpa o último erro após conclusão bem-sucedida de um comando Terminal.
- Adicionados testes de validação de repositórios/recursos GitHub e parsing de categorias WordPress.
- Versão Android incrementada para versionCode 16 / versionName 1.9.2.

### Limites de validação
- Build Android integral, testes Gradle/JUnit e testes contra contas reais GitHub/WordPress continuam dependentes de Android SDK/Gradle e credenciais fornecidas pelo utilizador.
- Os módulos `despertar/` não foram alterados nesta versão.


## 1.10.0 — Integração nativa do Espelhar-X (2026-11-05)

- Incorporado o Espelhar-X como módulo Kotlin/Jetpack Compose dentro do HubOps; não é uma segunda aplicação.
- Editor de ficheiros com workspace privado, abertura, gravação e análise local.
- Análise incremental com cache SHA-256, extração heurística de símbolos e deteção inicial de delimitadores, defaults mutáveis, `except:` e possível código morto em Python.
- Pipeline local para ficheiros suportados, sem executar nem modificar os ficheiros analisados.
- Execução explícita de scripts Shell através do ShellRunner já existente, preservando timeout e limite de saída.
- Despertar, GitHub, WordPress, Feed e Terminal mantidos como módulos separados; nenhum ficheiro do pacote Despertar foi alterado.
- Não foram portados o servidor HTTP aberto, mocks do frontend, Vue/Capacitor, Chaquopy nem alegações de sandbox proot/latência submilissegundo.
- Limitação: análise heurística, não um compilador/parser completo; Python, JavaScript, Kotlin, Rust e outras linguagens não são executadas pelo novo editor. A execução Shell ocorre no UID da aplicação, não num contentor isolado.

## 1.14.0 — Espelhar-X Build Intelligence (2026-11-05)
- Adicionada pré-verificação Android ao pipeline: manifesto, Gradle raiz, settings, build do módulo app, fontes e completude do Wrapper.
- A pré-verificação separa bloqueios de avisos e não declara sucesso de compilação.
- Interface apresenta o diagnóstico e explicita que SDK, resolução de dependências, compilação e APK não foram validados.
- Ampliada a cobertura JUnit para reparações com hash/backup, traversal, importação sem sobrescrita, grafo e Component Fusion.
- Versão Android: 1.14.0 (versionCode 21).
- Validação Kotlin isolada e smoke test JVM executados; build Android integral, JUnit via Gradle e JNI Android continuam pendentes. Ver `docs/VALIDACAO_1.14.0.md`.


## 1.16.0 — Espelhar-X Repair Rollback
- Adicionada reversão explícita de reparações com validação do conteúdo atual e do backup original por SHA-256.
- Backup de auditoria arquivado após reversão; sem sobrescrita silenciosa.
- Interface apresenta alterações aplicadas durante a sessão e disponibiliza ação de reversão.
- Testes acrescentados para reversão bem-sucedida e conflito após edição posterior.

