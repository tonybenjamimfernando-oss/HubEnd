/**
 * Catálogo dos motores HubOps integrados no HubEnd.
 *
 * Fonte: auditoria do código HubOps 1.29.0 (docs/AUDITORIA.md) e registo nativo
 * dev.hubops.core.integration.HubOpsEngineRegistry. Cada capacidade tem um estado documentado
 * e a evidência (ficheiro/teste) que o sustenta. Nada aqui é inferido da interface.
 */

export type FeatureStatus =
  | "TESTADA"
  | "IMPLEMENTADA"
  | "PARCIAL"
  | "PREPARADA"
  | "PENDENTE"
  | "BLOQUEADA";

export const STATUS_LABEL: Record<FeatureStatus, string> = {
  TESTADA: "Implementada e testada",
  IMPLEMENTADA: "Implementada",
  PARCIAL: "Parcial",
  PREPARADA: "Preparada, não operacional",
  PENDENTE: "Pendente",
  BLOQUEADA: "Bloqueada por dependência externa",
};

export const STATUS_ORDER: FeatureStatus[] = [
  "TESTADA",
  "IMPLEMENTADA",
  "PARCIAL",
  "PREPARADA",
  "PENDENTE",
  "BLOQUEADA",
];

/** IDs do registo nativo (enum EngineId). SNSE, NSC e GoPA vivem dentro do HUBOPS. */
export type RegistryEngineId = "HUBOPS" | "DESPERTAR" | "ESPELHAR_X" | "VISLUMBOPS";
export type FlowMode = "GOVERNED_MUTATION" | "READ_ONLY_ANALYSIS" | "EVIDENCE_ONLY";

export const FLOW_LABEL: Record<FlowMode, string> = {
  GOVERNED_MUTATION: "Mutação governada",
  READ_ONLY_ANALYSIS: "Só leitura",
  EVIDENCE_ONLY: "Só evidência",
};

export interface Capability {
  name: string;
  detail: string;
  status: FeatureStatus;
  evidence: string;
}

export type EngineSlug =
  | "hubops-core"
  | "espelhar-x"
  | "despertar"
  | "vislumbops"
  | "snse"
  | "gopa"
  | "nsc";

export interface EngineSpec {
  slug: EngineSlug;
  name: string;
  code: string;
  tagline: string;
  role: string;
  /** Motor no registo nativo, ou o motor que o aloja (Omega -> HUBOPS). */
  registryId: RegistryEngineId;
  hostedBy?: RegistryEngineId;
  sourcePath: string;
  approxLines: number;
  capabilities: Capability[];
  dependencies: string[];
  limitations: string[];
}

/** Fluxos autorizados declarados em HubOpsEngineRegistry.kt (fonte de verdade). */
export const REGISTRY_FLOWS: { source: RegistryEngineId; target: RegistryEngineId; mode: FlowMode }[] = [
  { source: "HUBOPS", target: "DESPERTAR", mode: "GOVERNED_MUTATION" },
  { source: "HUBOPS", target: "ESPELHAR_X", mode: "GOVERNED_MUTATION" },
  { source: "HUBOPS", target: "VISLUMBOPS", mode: "EVIDENCE_ONLY" },
  { source: "DESPERTAR", target: "ESPELHAR_X", mode: "GOVERNED_MUTATION" },
  { source: "ESPELHAR_X", target: "DESPERTAR", mode: "READ_ONLY_ANALYSIS" },
  { source: "VISLUMBOPS", target: "HUBOPS", mode: "EVIDENCE_ONLY" },
];

export const ENGINES: EngineSpec[] = [
  {
    slug: "hubops-core",
    name: "HubOps Core",
    code: "CORE",
    tagline: "Orquestração, registo de motores e ledger comum",
    role: "Coordena os motores autónomos por contratos: nenhuma alteração entre motores é aceite sem proveniência, impacto, aprovação e evidência de validação.",
    registryId: "HUBOPS",
    sourcePath: "android/hubops/src/main/java/dev/hubops/core",
    approxLines: 2400,
    capabilities: [
      { name: "Registo de motores e políticas de fluxo", detail: "Quatro motores e seis fluxos autorizados; fluxos não declarados são recusados.", status: "TESTADA", evidence: "HubOpsEngineRegistryTest.kt" },
      { name: "Transferências ZIP em duas fases", detail: "Proposta com SHA-256, impacto e colisões → staging → validação → promoção.", status: "TESTADA", evidence: "HubOpsOrchestratorTest.kt" },
      { name: "Reparação controlada com rollback", detail: "Um ficheiro por plano, token de aprovação explícito e backup verificado por hash.", status: "TESTADA", evidence: "ControlledRepairEngineTest.kt" },
      { name: "Recuperação de transações interrompidas", detail: "Diário de estados e deteção de transações a meio.", status: "TESTADA", evidence: "TransactionRecoveryEngineTest.kt" },
      { name: "Reconciliação e plano de recuperação", detail: "Compara proposta, artefacto, staging, destino e ledger.", status: "IMPLEMENTADA", evidence: "HubOpsOrchestrator.kt:425-483" },
      { name: "Ledger de verificações externas", detail: "Regista auditorias VislumbOps sem alterar o projeto.", status: "TESTADA", evidence: "HubOpsExternalVerificationLedgerTest.kt" },
      { name: "Fluxo profissional Pro", detail: "Snapshot → AST → auditoria APK → avaliação cruzada → reparação aprovada → nova auditoria.", status: "TESTADA", evidence: "ProfessionalWorkflowCoordinatorTest.kt" },
      { name: "AST partilhada (Tree-sitter)", detail: "Kotlin, Java e Python; ficheiros sem gramática são contados como indisponíveis.", status: "PARCIAL", evidence: "TreeSitterAstAnalyzer.kt:130-174" },
      { name: "Configuração cifrada", detail: "Token GitHub e credenciais WordPress em EncryptedSharedPreferences.", status: "IMPLEMENTADA", evidence: "SecureConfig.kt" },
      { name: "Política HTTP", detail: "Só HTTPS, limites de resposta e validação de entradas.", status: "TESTADA", evidence: "HttpPolicyTest.kt, ValidationTest.kt" },
    ],
    dependencies: ["Kotlin Coroutines/Flow", "AndroidX Security", "Tree-sitter (KTreeSitter + gramáticas)"],
    limitations: [
      "O ledger local é um ficheiro de texto: não é inviolável nem assinado por linha.",
      "O EventBus nativo descarta eventos antigos (DROP_OLDEST): não é um registo durável.",
      "recordExternalVerification não valida a política de fluxo quando chamado diretamente.",
    ],
  },
  {
    slug: "espelhar-x",
    name: "Espelhar-X",
    code: "IDE",
    tagline: "IDE offline, análise AST e pipeline de validação",
    role: "Espaço de desenvolvimento: editor com workspace privado, análise estática incremental, mapa de dependências, comparação e propostas de reparação.",
    registryId: "ESPELHAR_X",
    sourcePath: "android/hubops/src/main/java/dev/hubops/espelhar",
    approxLines: 760,
    capabilities: [
      { name: "Editor e workspace privado", detail: "Abrir, analisar e guardar ficheiros dentro do workspace da app.", status: "TESTADA", evidence: "EspelharEngineTest.kt" },
      { name: "Importação/exportação ZIP e GitHub", detail: "Importa repositórios GitHub ou ZIP para um prefixo isolado.", status: "TESTADA", evidence: "EspelharEngineTest.kt" },
      { name: "Grafo de dependências", detail: "Imports e referências entre ficheiros do projeto.", status: "TESTADA", evidence: "EspelharProjectServicesTest.kt" },
      { name: "Comparação snapshot ↔ workspace", detail: "Diferenças entre uma fonte remota e o workspace.", status: "TESTADA", evidence: "EspelharProjectServicesTest.kt" },
      { name: "Propostas, aplicação e rollback de reparações", detail: "Reparações com backup e reversão.", status: "TESTADA", evidence: "EspelharProjectServicesTest.kt" },
      { name: "Pré-verificação de build", detail: "Análise de prontidão; não executa o Gradle.", status: "PARCIAL", evidence: "EspelharProjectServices.kt:80" },
    ],
    dependencies: ["HubOps Core (AST partilhada)", "Despertar (análise só de leitura)"],
    limitations: ["A validação de build é preparatória: a compilação real corre no Termux ou no CI."],
  },
  {
    slug: "despertar",
    name: "Despertar",
    code: "FORGE",
    tagline: "Forja de projetos, estudo de repositórios e APKs",
    role: "Estuda repositórios, gera projetos Kotlin/Compose, compara e funde componentes, e delega builds e análise de APK ao Termux.",
    registryId: "DESPERTAR",
    sourcePath: "android/hubops/src/main/java/dev/hubops/despertar",
    approxLines: 2400,
    capabilities: [
      { name: "Estudo estrutural de repositórios", detail: "Árvore, builds, manifestos, commits, releases e revisão heurística de até 8 ficheiros.", status: "IMPLEMENTADA", evidence: "DespertarService.kt:34-168" },
      { name: "Gerador de projetos Android", detail: "Projeto Kotlin/Compose completo a partir de um blueprint.", status: "TESTADA", evidence: "AndroidProjectGeneratorTest.kt" },
      { name: "Inteligência e arquitetura de projeto", detail: "Métricas, símbolos e achados por projeto.", status: "TESTADA", evidence: "ProjectIntelligenceTest.kt" },
      { name: "Similaridade de código", detail: "Compara dois conjuntos de fontes.", status: "TESTADA", evidence: "CodeSimilarityEngineTest.kt" },
      { name: "Fusão de componentes", detail: "Relatório de compatibilidade entre projeto principal e referência.", status: "TESTADA", evidence: "ComponentFusionTest.kt" },
      { name: "Diagnóstico de builds", detail: "Recomendações a partir de stdout/stderr e código de saída.", status: "TESTADA", evidence: "BuildRepairAdvisorTest.kt" },
      { name: "Build e JADX/APKTool via Termux", detail: "RUN_COMMAND com callback de resultado.", status: "BLOQUEADA", evidence: "TermuxBridge.kt:37-175 — exige Termux, permissão e ferramentas" },
      { name: "Desbloqueio pago validado", detail: "Hoje é um interruptor local de desenvolvimento.", status: "PENDENTE", evidence: "TermuxBridge.kt:279-285 — falta Google Play Billing" },
    ],
    dependencies: ["GitHub REST API", "Termux ≥ 0.109 (RUN_COMMAND)", "Espelhar-X (mutação governada)"],
    limitations: [
      "Não copia nem transforma código de terceiros; escreve apenas no ramo despertar/atribuicoes.",
      "O desbloqueio pago não está protegido: precisa de Google Play Billing com validação no servidor.",
    ],
  },
  {
    slug: "vislumbops",
    name: "VislumbOps",
    code: "AUDIT",
    tagline: "Auditoria de APK, cadeia de fornecimento e evidências",
    role: "Observa e diagnostica artefactos: integridade, assinatura, permissões, segredos, dependências e CVEs, com cadeia de evidências e atestação local.",
    registryId: "VISLUMBOPS",
    sourcePath: "android/hubops/src/main/kotlin/com/hubops/vislumbrops",
    approxLines: 2200,
    capabilities: [
      { name: "Integridade e assinatura APK", detail: "Hash, estrutura ZIP e verificação de assinatura.", status: "IMPLEMENTADA", evidence: "ApkInspector.kt, ApkSignatureVerificationEngine.kt" },
      { name: "Inventário da cadeia de fornecimento", detail: "Manifesto do artefacto com verificações de arquivo limitadas.", status: "TESTADA", evidence: "ArtifactSupplyChainEngineTest.kt" },
      { name: "Comparação de versões CVE", detail: "Intervalos de versões vulneráveis.", status: "TESTADA", evidence: "CveCatalogTest.kt" },
      { name: "Dependências vulneráveis", detail: "Base local de 11 CVEs; versões nativas nem sempre identificáveis.", status: "PARCIAL", evidence: "VALIDACAO_1.29.0.md — Limites" },
      { name: "Segredos e padrões inseguros", detail: "Chaves, tokens, TLS, WebView e componentes exportados.", status: "IMPLEMENTADA", evidence: "SecretScanner.kt, StaticAnalyzer.kt" },
      { name: "Evidências, atestação local e relatórios", detail: "Score, cadeia de evidências e relatório Markdown/JSON.", status: "IMPLEMENTADA", evidence: "VerificationOrchestrator.kt:142-180" },
      { name: "Execução isolada de APK", detail: "Devolve SKIPPED: nenhum APK é instalado ou executado.", status: "PREPARADA", evidence: "SandboxRunner.kt:14-27" },
      { name: "Profiling dinâmico", detail: "Requer laboratório ou emulador externo.", status: "BLOQUEADA", evidence: "PerformanceProfiler.kt:8-18" },
    ],
    dependencies: ["HubOps Core (só evidência)", "Android PackageManager"],
    limitations: [
      "A atestação local não equivale a certificação independente.",
      "Ausência de achados não prova ausência de vulnerabilidades.",
    ],
  },
  {
    slug: "snse",
    name: "SNSE",
    code: "NEURO",
    tagline: "Sharded Neuro-Symbolic Ensemble",
    role: "Encaminha achados para especialistas por CWE (simbólico) e arbitra propostas de patch. Os especialistas neurais só correm com runtime e modelos reais instalados.",
    registryId: "HUBOPS",
    hostedBy: "HUBOPS",
    sourcePath: "android/hubops/src/main/java/dev/hubops/omega/ai",
    approxLines: 470,
    capabilities: [
      { name: "Roteamento simbólico por CWE", detail: "Sete especialistas: SQLI, CRYPTO, AUTH, NETWORK, STORAGE, LOGGING, DESERIALIZATION.", status: "TESTADA", evidence: "OmegaEnginesTest.kt:26-85" },
      { name: "Arbitragem simbólica de patches", detail: "Escolhe e valida propostas contra o código original.", status: "TESTADA", evidence: "OmegaEnginesTest.kt" },
      { name: "Instalação assinada de modelos e runtime", detail: "Manifestos Ed25519 e SHA-256 antes de ativar.", status: "IMPLEMENTADA", evidence: "SignedModelInstaller.kt, OmegaEngineSuite.kt:43-80" },
      { name: "Inferência neural (llama.cpp)", detail: "Sem pesos GGUF nem runtime no APK, usa NoShardInferenceRuntime.", status: "PREPARADA", evidence: "SnseEngine.kt:58-61" },
      { name: "Precisão, recall, RAM e latência", detail: "Não medidos.", status: "PENDENTE", evidence: "OMEGA_1.28.0_IMPLEMENTACAO.md" },
    ],
    dependencies: ["VislumbOps (achados)", "llama.cpp CLI arm64-v8a (externo)", "Modelos GGUF assinados (externos)"],
    limitations: ["O runtime CLI é experimental no Android.", "Os patches gerados continuam a ser propostas."],
  },
  {
    slug: "gopa",
    name: "GoPA",
    code: "LEDGER",
    tagline: "Atestações Ed25519, Merkle e quórum",
    role: "Assina recibos de auditoria, calcula compromissos Merkle e quórum entre pares; inclui servidor Go de referência.",
    registryId: "HUBOPS",
    hostedBy: "HUBOPS",
    sourcePath: "android/hubops/src/main/java/dev/hubops/omega/merkle · tools/gopa",
    approxLines: 720,
    capabilities: [
      { name: "Assinatura, deduplicação, quórum e Merkle", detail: "Verificação Ed25519 e consenso por artefacto.", status: "TESTADA", evidence: "OmegaEnginesTest.kt:118-170" },
      { name: "Cliente HTTP GoPA", detail: "Publicar atestação, consultar consenso e gossip.", status: "IMPLEMENTADA", evidence: "GopaClient.kt" },
      { name: "Servidor Go com persistência", detail: "/attest, /consensus, /gossip/delta, /health.", status: "TESTADA", evidence: "tools/gopa/main_test.go" },
      { name: "Rede de pares independentes", detail: "Requer servidores, TLS, tokens e diretório de chaves.", status: "BLOQUEADA", evidence: "OMEGA_1.28.0_IMPLEMENTACAO.md" },
      { name: "Consenso BFT geral / proteção Sybil", detail: "Fora do âmbito atual.", status: "PENDENTE", evidence: "OMEGA_1.28.0_IMPLEMENTACAO.md" },
    ],
    dependencies: ["Bouncy Castle (Ed25519)", "Servidor GoPA (Go 1.27)"],
    limitations: ["Não é consenso BFT geral.", "Token bearer partilhado não substitui mTLS."],
  },
  {
    slug: "nsc",
    name: "NSC",
    code: "SANDBOX",
    tagline: "Nested Sandbox Cascade",
    role: "Planeia a execução em camadas (análise JVM, PRoot, Termux delegado ou indisponível) com limites explícitos de tempo e memória.",
    registryId: "HUBOPS",
    hostedBy: "HUBOPS",
    sourcePath: "android/hubops/src/main/java/dev/hubops/omega/containers",
    approxLines: 350,
    capabilities: [
      { name: "Planeamento por camadas e recusa segura", detail: "Rejeita runtimes ausentes ou inseguros.", status: "TESTADA", evidence: "OmegaEnginesTest.kt:87-116" },
      { name: "Instalador de rootfs transacional", detail: "Valida SHA-256 e promove atomicamente.", status: "IMPLEMENTADA", evidence: "RootfsInstaller.kt" },
      { name: "Arranque PRoot/Alpine", detail: "PRoot e rootfs não incluídos no APK.", status: "PREPARADA", evidence: "OMEGA_1.28.0_IMPLEMENTACAO.md" },
      { name: "Workloads Python/Node/SQLite", detail: "A imagem base não os contém.", status: "BLOQUEADA", evidence: "OMEGA_1.28.0_IMPLEMENTACAO.md" },
    ],
    dependencies: ["PRoot (GPL-2.0+, externo)", "Alpine rootfs (externo)", "Termux (opcional)"],
    limitations: ["PRoot é virtualização de caminhos em espaço de utilizador, não isolamento de kernel."],
  },
];

export function getEngine(slug: string): EngineSpec | undefined {
  return ENGINES.find((e) => e.slug === slug);
}

export function statusBreakdown(capabilities: Capability[]): Record<FeatureStatus, number> {
  const out = Object.fromEntries(STATUS_ORDER.map((s) => [s, 0])) as Record<FeatureStatus, number>;
  for (const c of capabilities) out[c.status] += 1;
  return out;
}

export function allCapabilities(): Capability[] {
  return ENGINES.flatMap((e) => e.capabilities);
}

/** Fluxos de saída de um motor, segundo o registo nativo. */
export function outboundFlows(id: RegistryEngineId) {
  return REGISTRY_FLOWS.filter((f) => f.source === id);
}

/** Verdadeiro só se o registo autoriza exatamente este modo entre os dois motores. */
export function isFlowAllowed(source: RegistryEngineId, target: RegistryEngineId, mode: FlowMode): boolean {
  return REGISTRY_FLOWS.some((f) => f.source === source && f.target === target && f.mode === mode);
}
