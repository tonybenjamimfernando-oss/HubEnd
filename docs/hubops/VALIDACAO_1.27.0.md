# Validação 1.27.0 — Omega

## Implementação

- Adicionados `SnseEngine`, `SymbolicShardRouter`, `ShardModelManager` e `SymbolicPatchArbiter`.
- Adicionado `NestedSandboxCascade` com planos separados para análise estática JVM, PRoot user-space, Termux delegado e indisponibilidade.
- Adicionado `GopaConsensus` com Ed25519, evidências comprometidas por raiz Merkle, peer registry explícito, deduplicação e quórum calculado sobre peers elegíveis.
- Adicionado `OmegaEngineSuite` ao ciclo de vida de `HubOpsApplication`, capacidades ao registry e rota **Omega**.
- O resultado SNSE pode ser enviado ao Pro Flow; antes de criar um plano, o ViewModel verifica que o texto original é idêntico ao ficheiro atual no workspace do caso.

## Verificações executadas

- `kotlinc` compilou isoladamente o SNSE com um stub do modelo `Finding`: aprovado.
- `kotlinc` compilou NSC e GoPA: aprovado.
- `kotlinc` compilou `OmegaEngineSuite` e `HubOpsApplication` com stubs das APIs Android: aprovado (não equivale a build Android real).
- Compilação dos 14 métodos de teste Omega com stubs JUnit e modelo de `Finding`: aprovada.
- Execução dos 14 métodos de teste num harness JVM isolado: **14/14 aprovados**. O harness usou shim JUnit e não é a tarefa oficial Gradle/JUnit.
- Smoke tests adicionais: SNSE SQLi/low-confidence/JSON/runtime injetado/arbiter; NSC fallback JVM/UNIX, symlink e aprovação; GoPA assinatura Ed25519, quórum 7/10, rejeição de 6/10, duplicação, adulteração e Merkle: aprovados.
- Pacote final 1.27.0 validado com `unzip -t`: sem erros de integridade.

## Limitações que permanecem

- Não há pesos GGUF 1B, runtime nativo llama.cpp nem benchmarks de RAM/latência. O runtime predefinido é `NoShardInferenceRuntime`; SNSE opera em fallback simbólico até o integrador fornecer modelos licenciados e um runtime testado.
- O catálogo CWE offline do router cobre um subconjunto de IDs; não é uma base CWE/NVD completa. O arbiter é um filtro conservador de padrões, não prova que um patch é seguro ou correto.
- NSC não inclui binário proot nem rootfs. JVM significa análise estática, não sandbox de execução. PRoot não é Docker nem isolamento de kernel; a rede não é isolada e o monitor de RAM é best-effort, sem cgroups.
- A opção Termux é detetada e apresentada como delegação possível; execução arbitrária genérica não foi implementada. As tarefas devem continuar a usar os comandos allowlisted de `TermuxJobs`.
- GoPA tem API criptográfica e agregação local em memória, mas ainda não tem armazenamento persistente de receipts, diretório de peers com gestão de ciclo de vida, revogação/rotação de chaves ou transporte gossip. Não é BFT geral nem resistente a Sybil/colusão.
- Ed25519 depende do provider do dispositivo Android; falha fechada se não estiver disponível.
- Gradle/JUnit oficial, Android Lint, `assembleDebug`, testes instrumentados, UI e validação em dispositivo continuam pendentes porque não existe Gradle/Android SDK configurado neste ambiente.
