# Omega 1.28.0 — preparação de runtime SNSE, NSC e GoPA

## Objetivo desta revisão

Esta revisão transforma os três rascunhos de pipeline em componentes de preparação e integração que podem ser testados separadamente. Não declara automaticamente os critérios de hardware aprovados: são necessários NDK, host Linux, GPU para fine-tuning e dispositivos Android reais.

## SNSE

- `tools/snse/Makefile` fixa a origem llama.cpp no tag `b2678`, regista o commit resolvido e compila `llama-cli` arm64, `libllama.so`, `libggml*.so` e `libc++_shared.so` para empacotamento Android.
- `AndroidLlamaCliRuntime` invoca o executável assinado com argumentos separados (sem shell), um processo/modelo de cada vez, timeout, diretório de saída limitado e fallback por `Result.failure`.
- `OmegaEngineSuite.installLlamaCliRuntime()` verifica o manifesto de release assinado, hash e cabeçalho ELF antes de anexar o runtime ao `SnseEngine`. A chave pública Ed25519 é embutida em `BuildConfig`; a chave privada fica exclusivamente no ambiente de release.
- A consola Omega permite instalar o runtime, escolher o nome do shard e importar cada GGUF pelo seletor de documentos; também oferece bootstrap PRoot/Alpine explícito.
- `SignedModelInstaller.installSnseShard()` importa GGUF escolhido pelo seletor de documentos. Verifica manifesto Ed25519, tamanho, SHA-256 e assinatura individual antes de ativar o ficheiro; preserva/restaura a versão anterior se a promoção falhar.
- Os sete GGUF são distribuídos em `dist/snse-models-arm64.zip`, **fora do APK**. O APK contém apenas manifestos e binários necessários; não é razoável embutir 7 modelos 1B quantizados no APK.
- Fine-tuning usa collator próprio que preserva `-100` nos tokens do prompt. Dataset GHSA tenta reconstruir pares reais a partir de hunks de commits; só aceita extensões de código e descarta diffs incompletos/sem par.
- `eval_precision.py` define precisão/recall como correspondência exata de saída em conjunto held-out. Esta métrica é deliberadamente estrita e não substitui revisão semântica, testes, compilação ou avaliação de segurança.

### Limitações SNSE

- O runtime CLI é experimental no Android: `execve` do binário em `filesDir`, resolução dinâmica de `libllama` e desempenho devem ser verificados em cada família de dispositivos. Se o sistema impedir execução, o motor permanece sem runtime e não inventa inferência. O limite RSS observa o processo filho em `/proc`; se a leitura não estiver disponível, não é uma medição integral da memória total da app.
- O modelo base TinyLlama 1.1B não garante capacidade especializada. A qualidade depende de dados de treino, licença, validação, prompt e avaliação.
- Os patches gerados continuam propostas. O árbitro e Pro Flow não provam correção nem ausência de regressões.
- A compilação `armeabi-v7a` é tentativa experimental; o alvo de release desta revisão é `arm64-v8a`.

## NSC

- PRoot é compilado como **executável ELF** e colocado em `assets/native/arm64-v8a/proot`, nunca renomeado como `.so`.
- `libtalloc.so` e `libc++_shared.so` são empacotados em `jniLibs` para resolução dinâmica. O bootstrap prepara também o alias de SONAME `libtalloc.so.2` em armazenamento privado e o launcher exporta `LD_LIBRARY_PATH` para ambas as pastas.
- Alpine é guardado em `assets/oci/alpine_3.24.4.tar.gz`. O manifesto inclui SHA-256 e assinatura Ed25519.
- `RootfsInstaller` verifica hash antes de extrair, limita entradas e bytes expandidos, rejeita hardlinks/dispositivos/FIFO e impede links simbólicos que escapem do rootfs. A instalação é staged e promovida com backup/rollback.
- O bootstrap é explícito via `OmegaEngineSuite.bootstrapAlpine(releaseKey)`. Não executa instalação pesada nem comandos não confiáveis automaticamente em `Application.onCreate`.

### Limitações NSC

- O rootfs foi atualizado para Alpine 3.24.4, patch disponível no canal v3.24 nesta data; a versão deve continuar a ser revista antes de cada release. A imagem minirootfs base não contém Python, Node, SQLite CLI nem os dez workloads do rascunho.
- PRoot é virtualização de caminhos em espaço de utilizador, não isolamento de kernel. Não deve ser usado para executar código hostil sem controlos adicionais.
- A compilação PRoot/talloc para Bionic pode exigir patches específicos da versão upstream; o pipeline falha fechado se não produzir ELF executável. O teste `tools/nsc/test_nsc.sh` deve ser executado em dispositivo arm64.

## GoPA v2

- Protocolo Android/Go unificado com campos `evidenceHashes`, `issuedAtEpochMs`, `signatureBase64` e canonicalização `GOPA-ATTESTATION-V1`.
- Ed25519 autentica cada peer; HTTPS é obrigatório; bearer token protege o transporte; o servidor permite configurar CA para gossip TLS. A app inclui provider Bouncy Castle para manter Ed25519 disponível em Android API 26+, sem depender exclusivamente do provider do sistema.
- Peers elegíveis vêm de `peer-keys.json`; quórum é calculado sobre o conjunto elegível fixo: `floor(2N/3)+1`. Uma identidade pode emitir apenas um recibo imutável por artefacto.
- Ledger JSON é persistido por substituição atómica. Receipts são revalidados ao recuperar.
- Gossip propaga receipts assinados e valida novamente cada entrada no destino.

### Limitações GoPA

- Não é consenso BFT geral: não há eleição dinâmica, tolerância formal a partições, proteção Sybil, identidade organizacional forte nem prova de independência dos operadores.
- Token bearer partilhado é uma proteção de transporte inicial, não substitui mTLS e gestão individual de credenciais. Para produção, cada peer deve receber credenciais TLS/client próprias e rotação/revogação.
- O ledger JSON e gossip delta são MVPs: faltam paginação, compactação, limites operacionais por artefacto, backup/replicação e gestão/rotação de chaves.

## Build / release

1. Preparar dataset e treinar no host com GPU; nunca executar fine-tuning no telemóvel.
2. Definir `ANDROID_NDK_HOME` e `OMEGA_SIGNING_KEY` (chave privada Ed25519 fora do repositório). Criar um conjunto held-out independente com pelo menos 100 pares em `data/test_held_out.jsonl`; nenhum commit/código desse conjunto pode estar no treino.
3. Executar `tools/build-release.sh` em host Linux com JDK/Gradle/Android SDK, CMake, Git, OpenSSL, Python, ZIP e GPU/VRAM adequada. O script bloqueia o release se precision/recall exact-match não atingirem os limiares definidos.
4. O script cria APK debug, `dist/snse-models-arm64.zip` e manifestos assinados. Guardar a chave privada fora do projeto e nunca incluí-la no ZIP.
5. Importar cada GGUF a partir do pacote de modelos na aplicação e instalar PRoot/Alpine apenas após a verificação do manifesto.
6. Executar testes em dispositivo arm64; registar modelo/commit, versão Android, RAM livre, pico de RSS, latência, temperatura e resultado de cada caso.

## Estado de validação desta entrega

- Go: testes unitários com Ed25519, quórum fixo, duplicados/adulteração e persistência executados com sucesso em host.
- Python: scripts verificados sintaticamente; não foram descarregados advisories, treinados modelos nem quantizados pesos nesta sessão.
- Android/Kotlin: código e dependências adicionados; Gradle/Android SDK/NDK não estão instalados neste ambiente, portanto compilação Android e APK não foram confirmados.
- PRoot/Alpine: não compilados nem instalados em dispositivo nesta sessão.
- Precisão, recall, RAM, latência e estabilidade térmica: não medidos; critérios permanecem pendentes.
