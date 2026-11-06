# Omega Engines — HubOps 1.27.0

Esta versão integra três submotores no ciclo de vida Android da aplicação, com uma rota operacional **Omega** e uma API comum em `HubOpsApplication.omegaEngines`.

## 1. SNSE — Sharded Neuro-Symbolic Ensemble

- `SymbolicShardRouter` classifica sinais para sete domínios: SQLi, criptografia, autenticação, rede/TLS, armazenamento, logging e desserialização.
- Um mapeamento CWE limitado e embutido pode priorizar um shard; não é uma base CWE/NVD completa. Confiança abaixo de 0,70 abre até dois candidatos. Sem sinais simbólicos, não carrega modelo e mantém fallback de revisão.
- `ShardModelManager` procura modelos GGUF por shard, estima um orçamento conservador e seleciona o menor ficheiro elegível. A estimativa é apenas aproximada e não substitui medição de RSS/VRAM nem metadados confiáveis.
- `ShardInferenceRuntime` é a fronteira de integração para llama.cpp ou outro runtime local. O runtime predefinido é `NoShardInferenceRuntime`.
- `SymbolicPatchArbiter` rejeita saídas vazias, confidence fora de 0..1, patches idênticos, cripto fraca introduzida e padrões perigosos; aplica limiares de confiança ponderados pela severidade e alinhamento CWE. Uma proposta aceite significa apenas **elegível para revisão**; não prova correção nem ausência de vulnerabilidades.
- A aplicação nunca aplica o patch diretamente. O ecrã Omega permite enviar a proposta ao Pro Flow, mas o `HubViewModel` exige que o texto original analisado corresponda byte a byte ao ficheiro atual do mesmo workspace antes de preparar o plano. A integração de reparação continua a ser feita por `ControlledRepairEngine`, com aprovação, backup, compilação e nova auditoria.

**Limitação de distribuição:** esta versão não inclui os sete pesos 1B, runtime nativo llama.cpp, licença/manifesto dos modelos ou medição de consumo. Sem um `ShardInferenceRuntime` real e modelos compatíveis, SNSE opera em modo `SYMBOLIC_ONLY`. Não se promete 700 MB por modelo, RAM de pico inferior a 1,5 GB nem latência de 3–8 segundos sem benchmark em dispositivos reais.

## 2. NSC — Nested Sandbox Cascade

- O planeador distingue `JVM_STATIC_ANALYSIS`, `PROOT_USERSPACE`, `TERMUX_DELEGATED` e `UNAVAILABLE`.
- JVM é deliberadamente análise estática: `ClassLoader` e filtros de bytecode não são tratados como fronteira de segurança.
- PRoot só fica elegível quando existe um binário ELF executável em `filesDir/native/proot`, um rootfs privado em `filesDir/rootfs/` e RAM disponível suficiente. O launcher não monta caminhos do host e exige aprovação explícita.
- O launcher aplica timeout e monitorização best-effort de RSS via `/proc`; não dispõe de cgroups e não garante contabilização de todos os processos descendentes.
- PRoot é virtualização de paths em user space, não Docker, não namespace de kernel e não proteção comprovada contra escape. A rede pode continuar acessível através do host; não existe isolamento de rede neste launcher. Não se executa workload quando as capacidades não estão verificadas.
- Termux é apenas detetado e apresentado como opção de delegação que exige consentimento. Não há execução arbitrária genérica; tarefas devem continuar a usar as allowlists do `TermuxJobs` existente.

**Limitação de distribuição:** não é incluído binário proot nem rootfs. Cargas UNIX ficam `UNAVAILABLE` ou `TERMUX_DELEGATED` não pronto até provisionamento e aprovação explícitos. Não se declara suporte PostgreSQL/Redis/NGINX/MongoDB sem rootfs, binários, testes e limites medidos.

## 3. GoPA — Gossip + Proof-of-Audit

- Cada receipt usa assinatura Ed25519 sobre artefacto, verdict, raiz Merkle, hashes de evidência, timestamp e peer ID.
- O diretório de peers é explícito e baseado em chaves públicas X.509; IDs repetidos e peers desconhecidos são rejeitados.
- Uma identidade pode contribuir no máximo uma receipt por artefacto. A verificação confirma assinatura, timestamp futuro limitado, lista de hashes e raiz Merkle.
- O quórum é calculado sobre o conjunto de peers elegíveis registados: `floor(2*M/3)+1`, não sobre o número de respostas recebidas.
- `gossipDelta` e `ingest` fornecem o núcleo de sincronização delta; o transporte de rede e persistência durável de receipts ainda não estão implementados nesta versão.

**Limitações de confiança:** isto é um quórum de atestação de auditoria, não um protocolo BFT geral nem uma blockchain. Não impede Sybil, colusão, comprometimento de chaves ou peers independentes apenas por software. A raiz Merkle compromete hashes fornecidos; terceiros ainda precisam obter os artefactos de evidência e recalcular os seus hashes. A chave privada de cada peer é fornecida pelo integrador; armazenamento protegido/Keystore, rotação e revogação de chaves ainda não foram implementados. A disponibilidade Ed25519 depende do provider Android; em dispositivos sem provider, a operação falha fechada.

## Integração na aplicação

- `HubOpsApplication` cria `OmegaEngineSuite` uma vez no arranque.
- `HubOpsEngineRegistry` anuncia as três capacidades Omega sob o motor coordenador HubOps.
- A rota **Omega** permite executar o router/arbiter SNSE em fallback simbólico, consultar os planos NSC e encaminhar um patch gerado ao Pro Flow. Antes de preparar o plano, o HubViewModel confirma que o texto original analisado é idêntico ao ficheiro atual do workspace. GoPA fica explicitamente indisponível na UI até configuração de peers confiáveis.
- A rota Pro Flow continua a ser o caminho de aprovação e reparação; nenhum dos três submotores contorna o workflow.

## Validação executada nesta revisão

- `kotlinc` compilou `SnseEngine.kt` isoladamente com stub do modelo `Finding`: aprovado.
- `kotlinc` compilou `NestedSandboxCascade.kt` e `GopaConsensus.kt`: aprovado.
- Execução dos testes unitários Omega no harness JVM isolado com shim JUnit: **14/14 aprovados** (não é JUnit/Gradle oficial).
- Smoke test SNSE: roteamento SQLi, confiança baixa com dois candidatos, parsing JSON de uma runtime injetada e rejeição de `Runtime.exec`/cripto fraca introduzida: aprovado.
- Smoke test NSC: JVM classificada como análise estática, workload UNIX sem runtime recusado e execução sem aprovação recusada: aprovado.
- Smoke test GoPA: 10 peers Ed25519, 7 PASS + 3 FAIL, quórum 7/10; receipt adulterada rejeitada: aprovado.
- Não foi possível executar Gradle/JUnit oficial, lint, assembleDebug ou instrumentação Android neste ambiente. A rota Compose e o ciclo completo no dispositivo permanecem por validar.
