/**
 * Matriz de integração HubOps + HybridApp → HubEnd (Entrega A).
 * Espelha docs/AUDITORIA.md; manter os dois sincronizados.
 */

export type Origin = "HubOps" | "HybridApp" | "Ambos" | "HubEnd";
export type Decision = "Integrar" | "Adaptar" | "Unificar" | "Planear" | "Corrigir";

export interface MatrixRow {
  component: string;
  origin: Origin;
  role: string;
  decision: Decision;
  note: string;
}

export const INTEGRATION_MATRIX: MatrixRow[] = [
  { component: "Motores HubOps (Core, Espelhar-X, Despertar, VislumbOps, Omega)", origin: "HubOps", role: "Funcionalidade principal", decision: "Integrar", note: "Código Kotlin preservado como módulo :hubops; sem remoções." },
  { component: "Interface Compose HubOps (9 ecrãs)", origin: "HubOps", role: "Consola nativa completa", decision: "Integrar", note: "Mantida como atividade nativa, aberta a partir do HubEnd." },
  { component: "Testes unitários HubOps (27 ficheiros)", origin: "HubOps", role: "Validação", decision: "Integrar", note: "Correm em :hubops:testDebugUnitTest." },
  { component: "Ferramentas GoPA (Go), SNSE e NSC (Python/Make)", origin: "HubOps", role: "Servidor de atestação e pipelines de modelos/rootfs", decision: "Integrar", note: "Em tools/; validadas no CI." },
  { component: "Shell Capacitor + WebView", origin: "HybridApp", role: "Infraestrutura híbrida Android", decision: "Adaptar", note: "Capacitor 7 (não 6) e React/TanStack (não Vue 3), como existe nos ficheiros." },
  { component: "EventBus, ApiClient, armazenamento local", origin: "HybridApp", role: "Serviços partilhados da interface", decision: "Integrar", note: "src/lib/core; o EventBus nativo HubOps continua independente." },
  { component: "EventBus", origin: "Ambos", role: "Comunicação entre módulos", decision: "Unificar", note: "Um por camada: Kotlin (motores) e TypeScript (interface); a ponte liga-os." },
  { component: "Cliente GitHub", origin: "Ambos", role: "Integração GitHub", decision: "Unificar", note: "Motores usam GitHubService (Kotlin); a interface usa a API pública para o estado do repositório e do CI." },
  { component: "Terminal", origin: "Ambos", role: "Execução local", decision: "Adaptar", note: "Fica na consola nativa; não é exposto à WebView por segurança." },
  { component: "Base CVE local", origin: "Ambos", role: "Auditoria de dependências", decision: "Unificar", note: "Fonte única: res/raw/cve_database.json do VislumbOps." },
  { component: "Gradle: AGP 8.10.1 / Kotlin 2.2.21 vs Capacitor 7 (AGP 8.7.2, Java 21)", origin: "Ambos", role: "Compilação", decision: "Adaptar", note: "Build raiz único com AGP 8.10.1, Java 21 e compileSdk 36." },
  { component: "minSdk 23 (Capacitor) vs 26 (HubOps)", origin: "Ambos", role: "Compatibilidade", decision: "Adaptar", note: "minSdk 26 para todo o APK." },
  { component: "FileProvider duplicado", origin: "Ambos", role: "Partilha de ficheiros", decision: "Unificar", note: "Um único provider; caminhos HubOps fundidos." },
  { component: "5 erros de compilação HubOps 1.29.0", origin: "HubOps", role: "Qualidade", decision: "Corrigir", note: "Process.pid(), escapes inválidos e botões Compose fora de item {}." },
  { component: "Ponte HubEndEngines", origin: "HubEnd", role: "Comunicação interface ↔ motores", decision: "Integrar", note: "Plugin Kotlin com contrato JSON testado." },
  { component: "SQLite, biometria, notificações", origin: "HybridApp", role: "Serviços nativos", decision: "Planear", note: "Fase 1 do shell, após validar o APK base." },
  { component: "llama.cpp/JNI, PRoot, Google Play Billing", origin: "HubOps", role: "Runtimes e serviços pagos", decision: "Planear", note: "Fases 1, 3 e 4 do prompt; dependem de binários, modelos e Play Console." },
];

export interface Issue {
  severity: "Alta" | "Média" | "Baixa";
  title: string;
  detail: string;
  resolution: string;
}

export const ISSUES: Issue[] = [
  { severity: "Alta", title: "HubOps 1.29.0 não compilava", detail: "5 erros Kotlin: Process.pid() inexistente em Android, escapes '\\\\n' inválidos e Button fora de item {} num LazyColumn.", resolution: "Corrigido no HubEnd sem remover funcionalidades." },
  { severity: "Alta", title: "APK 0.1.0 fechava ao abrir", detail: "Uma regra de texto do SNSE com '}' sem escape é aceite pela JVM mas rejeitada pelo Android (ICU), e a aplicação fechava no arranque.", resolution: "Corrigido em 0.1.1; os 171 testes HubOps passam também num emulador Android 11." },
  { severity: "Alta", title: "Consola nativa fechava no ecrã Feed", detail: "O '%' da bateria entrava no padrão de format() (UnknownFormatConversionException).", resolution: "Corrigido em 0.1.1; teste no emulador abre os 10 ecrãs da consola." },
  { severity: "Alta", title: "Desbloqueio pago do Despertar é um interruptor local", detail: "TermuxBridge.kt:279-285 devolve config.devUnlocked.", resolution: "Pendente: Google Play Billing com validação no servidor (Fase 4)." },
  { severity: "Alta", title: "Execução de comandos exposta a conteúdo web", detail: "A WebView pode carregar um endereço remoto.", resolution: "A ponte não expõe o terminal nem o Termux; ficam na consola nativa." },
  { severity: "Alta", title: "Ledger local sem assinatura", detail: "integration-ledger.log usa appendText e Base64.", resolution: "Planeado: encadear linhas por SHA-256 e assinar com GoPA." },
  { severity: "Média", title: "Gramáticas Tree-sitter exigem compileSdk 36", detail: "Verificação de metadados AAR falhava com compileSdk 34/35.", resolution: "compileSdk 36; targetSdk 35." },
  { severity: "Média", title: "Monitorização de memória dependia de pid()", detail: "Sem PID, o limite de RSS seria ignorado.", resolution: "PID por reflexão; sem PID a execução é recusada." },
  { severity: "Média", title: "Base CVE pequena", detail: "11 entradas, a confirmar no NVD.", resolution: "Mantida; marcada como parcial." },
  { severity: "Baixa", title: "Prompt indica Vue 3 e Capacitor 6", detail: "O HybridApp fornecido é React + TanStack + Capacitor 7.", resolution: "Seguido o código real, como o prompt pede." },
];

export interface Phase {
  id: string;
  title: string;
  status: "concluída" | "em curso" | "pendente" | "bloqueada";
  criteria: string[];
}

export const PHASES: Phase[] = [
  { id: "A", title: "Auditoria", status: "concluída", criteria: ["Inventário dos dois projetos", "Matriz de integração", "Problemas identificados", "Arquitetura proposta"] },
  { id: "0", title: "Aplicação Android funcional", status: "em curso", criteria: ["Gradle Wrapper 8.11.1", "testDebugUnitTest", "assembleDebug", "Arranque e 10 ecrãs da consola testados em emulador Android 11", "Confirmação num telemóvel real (requer o utilizador)"] },
  { id: "1", title: "Motor nativo e execução", status: "pendente", criteria: ["llama.cpp via CMake/JNI arm64-v8a", "Modelos em filesDir com SHA-256", "Inferência real em dispositivo"] },
  { id: "2", title: "Inteligência avançada", status: "pendente", criteria: ["Especialistas SNSE neurais", "Latência, RAM, precisão e recall medidos"] },
  { id: "3", title: "Serviços e ambiente de execução", status: "pendente", criteria: ["Termux RUN_COMMAND", "PRoot com GPL e rootfs externo"] },
  { id: "4", title: "Serviços pagos", status: "pendente", criteria: ["Google Play Billing", "Validação de compras no servidor"] },
];
