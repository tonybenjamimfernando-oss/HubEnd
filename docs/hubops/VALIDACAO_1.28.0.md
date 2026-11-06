# Validação 1.28.0 — estado efetivamente observado

## Executado nesta sessão

| Verificação | Resultado | Âmbito |
|---|---|---|
| `go test -race -count=1 ./...` | PASS | Testes Go locais: quórum 7/10, quórum não reduzido por votos ausentes, assinatura adulterada, recibo duplicado/conflitante e recuperação do ledger |
| Assinatura Ed25519 de sete artefactos de teste + manifesto | PASS | Teste descartável com ficheiros falsos e chave temporária; não são modelos reais |
| `python3 -m py_compile tools/snse/*.py tools/nsc/*.py` | PASS | Sintaxe Python |
| `bash -n` nos scripts SNSE/NSC/GoPA/release | PASS | Sintaxe shell |
| Parse YAML de CI e Docker Compose | PASS | Estrutura YAML |
| Dry-run de Makefiles | PASS | Parsing de regras; nenhum download/compilação executado |
| Compilação Kotlin de Omega core | PASS | `kotlinc` com stub para Finding |
| Compilação Kotlin de runtime/cliente/instaladores | PASS | `kotlinc` com stubs de Android, JSON e TAR + coroutines; não substitui compilação contra SDK/dependências reais |
| `unzip -t` da origem 1.27.0 | PASS | Arquivo de entrada íntegro |

## Não executado / não aprovado

- Gradle/JUnit oficial, lint, APK debug/release e verificação real do `BuildConfig`.
- NDK r26+, CMake e compilação de llama.cpp `b2678` para Android arm64.
- Compilação PRoot/talloc para Bionic e compatibilidade de ptrace/execve no Android.
- Download de advisories, reconstrução de corpus, fine-tuning real dos sete especialistas e quantização dos GGUF.
- Precision ≥ 0.85 e recall ≥ 0.75 em 100 casos held-out.
- Instalação/importação de modelos com chave de release real.
- Inferência llama.cpp num dispositivo Android; RAM total, RSS filho, latência, temperatura e crashes.
- Instalação Alpine em dispositivo e teste PRoot de workloads/visibilidade de `/system`.
- Deployment Docker de três peers, TLS com CA real, gossip de rede e teste Android ↔ Go end-to-end.
- Consenso ≤ 50 ms para 100 receipts e tamanho final do binário Go.

## Limites importantes

- O código de instalação e os pipelines foram acrescentados, mas não existe um modelo treinado nem binário PRoot/llama-cli nesta distribuição-fonte.
- O APK não deve conter sete GGUF: o pacote de modelos é separado para evitar tamanho excessivo.
- Alpine 3.24.4 é uma base antiga e não deve ser promovida a produção sem atualização para uma versão ainda suportada e novo hash/manifesto.
- PRoot não é isolamento de kernel. GoPA é quorum de atestação sobre peers configurados, não BFT geral.
- Nenhum número de precisão, RAM, latência ou disponibilidade é declarado como atingido nesta sessão.
