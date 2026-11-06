# Despertar — capacidades e limites

## Pesquisa e estudo GitHub

- Consulta metadados, licença SPDX, linguagens, tópicos e excerto do README.
- Analisa a árvore Git recursiva quando a API a disponibiliza.
- Conta ficheiros com extensões de código conhecidas, caminhos de teste e workflows GitHub Actions.
- Deteta sistemas de build e manifestos comuns de dependências.
- Lista até cinco commits recentes e a release mais recente.
- Exporta relatório Markdown e mantém o fluxo de atribuições separado do ramo principal.

A auditoria é estrutural e heurística. Não instala dependências, não executa código, não deteta todas as vulnerabilidades e não deve ser interpretada como certificação de segurança. Contagens podem ser parciais quando a árvore GitHub vem truncada ou não pode ser consultada.

## Entrada e análise de APK

- Seletor Android SAF: o utilizador escolhe explicitamente o ficheiro.
- Cópia local para `Download/HubOps`, sem upload remoto.
- Verificação inicial de assinatura ZIP, limite de 500 MB e SHA-256 para identificação/integridade da cópia.
- Execução allowlisted de JADX ou APKTool através de Termux `RUN_COMMAND`.
- Pasta de saída nova em `~/despertar/<nome>`; não sobrescreve uma pasta existente.
- Receção de exit code, stdout, stderr e erros internos via `PendingIntent` (Termux >= 0.109).
- Último resultado persistido localmente e apresentado na interface.
- Revisão estática heurística de até oito ficheiros pequenos, selecionados entre código, manifesto e ficheiros de build. Sinaliza padrões como HTTP, WebView JavaScript, execução de processos, possíveis segredos literais, validação TLS permissiva e componentes exportados.

A revisão estática só examina os ficheiros efetivamente obtidos pela API GitHub; não é SAST completo e pode produzir falsos positivos/negativos. O relatório apresenta os sinais para revisão humana, não declara o repositório seguro.

O SHA-256 identifica os bytes importados; não prova que o APK seja seguro ou confiável. O cabeçalho ZIP é uma validação preliminar, não substitui a validação integral do pacote. JADX e APKTool precisam estar instalados e podem falhar em APKs ofuscados, danificados ou incompatíveis.

## Configuração Termux

```sh
termux-setup-storage
pkg update
pkg install jadx apktool
```

Em `~/.termux/termux.properties`:

```properties
allow-external-apps=true
```

Reiniciar o Termux e conceder à HubOps a permissão Android **Run commands in Termux environment**. O Termux também precisa de permissão de acesso ao armazenamento partilhado. A integração depende de Termux >= 0.109 para devolver o resultado por PendingIntent.

## Segurança e limites

- Apenas `jadx` e `apktool` são aceites pelo construtor de jobs.
- Caminho do APK e nome de saída são validados; caminhos com traversal são rejeitados.
- O script de shell é construído apenas com caminhos e nomes validados por allowlist.
- O utilizador confirma que possui autorização para analisar o APK.
- O APK não é enviado para GitHub nem para qualquer servidor.
- Resultados são limitados a 12.000 caracteres por fluxo na persistência local.
- O último resultado pode permanecer localmente até uma execução posterior; não inclui conteúdo do APK.
- O modo de desbloqueio atual é apenas de desenvolvimento, não é uma implementação de faturação.

## APK Lab e diagnóstico de desempenho (04-11-2026)

A importação agora faz uma segunda inspeção local do conteúdo ZIP/APK em streaming, sem extrair nem executar entradas. O relatório identifica `AndroidManifest.xml`, DEX (`classes*.dex`), bibliotecas nativas (`lib/**/*.so`), recursos, assets e ficheiros de assinatura indicativos em `META-INF`. A inspeção limita-se a 20.000 entradas, 64 MiB descomprimidos por entrada e 256 MiB no total; se um limite for atingido, o resultado é marcado como parcial. O cabeçalho ZIP e a presença do manifesto não validam a assinatura criptográfica nem descodificam o manifesto binário.

O ecrã inclui um diagnóstico Termux que verifica a presença de Java/JADX/APKTool/aapt, acesso ao diretório partilhado, configuração `allow-external-apps`, diretório de trabalho e espaço livre. Depois de uma descompilação, `Inspecionar saída` resume ficheiros, pastas, tamanho e uma amostra dos maiores ficheiros sem os modificar. Os nomes de saída são validados antes de formar comandos.

As operações iniciadas pelo ViewModel estão serializadas por `Mutex`: um segundo pedido é recusado enquanto outro está ativo, evitando corridas no estado `busy` e substituição acidental de resultados. O APK é copiado e inspecionado em `Dispatchers.IO`, com buffers fixos; não é carregado integralmente em memória.

Validações executadas nesta entrega: compilação isolada Kotlin do inspetor APK, compilação isolada do intake e da ponte Termux contra stubs Android, três scripts de shell gerados com `bash -n`, smoke test de um arquivo APK sintético e validação XML do Manifest. A compilação Gradle/Android e os testes instrumentados continuam pendentes: este pacote não contém `gradle-wrapper.jar`, o ambiente não tem Gradle nem Android SDK instalados.


## Project Forge + Android Builder (1.2.0)

O Project Forge gera um projeto Android Kotlin/Jetpack Compose independente em ZIP. O Android Builder agora liga esse artefacto a uma compilação real no dispositivo através do Termux: exporta o ZIP para `Download/HubOps/Projects`, verifica novamente o SHA-256 no Termux, valida o ZIP antes de extrair, executa Gradle 8.11.1–8.14.3 com JDK 17 e SDK Platform 34/Build Tools 34.0.0, e copia o APK debug para `Download/HubOps/Builds`.

O fluxo tem limites de memória/concorrência (`-Xmx1536m`, dois workers, sem daemon) e limites do arquivo (50 MiB comprimidos na exportação, 500 entradas e 150 MiB descomprimidos no script). Rejeita traversal, caminhos absolutos e symlinks; não substitui diretórios de trabalho nem APKs existentes. O SHA-256 impede que um ZIP alterado entre a exportação e a compilação seja extraído.

A compilação real depende do ambiente do dispositivo e pode falhar por falta de SDK, rede, espaço ou incompatibilidade de versões. O resultado é um APK **debug**, não uma release assinada. O processo não instala o APK automaticamente. Consulte `docs/ANDROID_BUILDER.md` para configuração, códigos de erro e diagnóstico.

Validação desta implementação: compilação isolada Kotlin do gerador, exportador MediaStore (com stubs Android) e ponte Termux; sintaxe Bash do script gerado; smoke test do construtor de jobs; validação ZIP normal, rejeição de traversal e symlinks; fluxo de build simulado com Gradle/JDK falsos e ferramentas ZIP reais; rejeição de arquivo alterado por SHA-256. A compilação Gradle/Android end-to-end no telefone ainda não foi executada nesta sessão.

## Component Fusion (1.7.0)

Depois de gerar o projeto principal, escolha um segundo ZIP Android no seletor de documentos. O Despertar compara os dois artefactos sem extrair ou executar ficheiros: colisões de caminhos de fontes, nomes de fontes repetidos, packages presentes nos dois projetos, coordenadas Gradle literais com versões divergentes e diferenças de permissões declaradas nos Manifestos. O relatório fornece um plano de integração faseado e pode ser copiado em Markdown.

O ZIP de referência tem limite comprimido de 30 MB. A leitura reutiliza os limites da Code Intelligence (2.000 entradas, 1.000 ficheiros relevantes, 2 MB por entrada e 20 MB descomprimidos). A análise Gradle é textual e não resolve version catalogs (`libs.versions.toml`), variáveis, plugins dinâmicos nem todas as formas de DSL. Não analisa compatibilidade binária, recursos Android, APIs, manifest merger, NDK ou licenças automaticamente. Não existe fusão automática nesta versão: qualquer integração requer autorização/licença, revisão manual, testes e build.

## Code Similarity & Refactoring Lab — 1.8.1

O laboratório compara o ZIP gerado com o ZIP de referência já carregado pelo Component Fusion. Reconhece fontes Python, Kotlin/Kotlin DSL e Java, emparelha por caminho relativo e apresenta similaridade textual e estrutural lexical, por ficheiro e em média. Ficheiros sem par são reportados separadamente.

**Precisão:** a métrica estrutural normaliza identificadores e literais, mas o motor atual não é um AST de compilador. Não deve ser interpretado como prova de equivalência semântica nem como percentagem de código reutilizado. A refatoração automática está bloqueada intencionalmente: antes de renomear símbolos é necessário um parser por linguagem e resolução de referências/escopos. Nenhuma substituição por regex é aplicada.

**Desempenho/limites:** máximo de 1.000 ficheiros-fonte, 1 MB de texto e 100.000 tokens por ficheiro. A leitura do ZIP mantém os limites centrais da Code Intelligence. O trabalho corre em `Dispatchers.IO` e é serializado pelo mutex de operações do ViewModel.
