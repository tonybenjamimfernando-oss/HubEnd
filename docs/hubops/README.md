# HubOps — plataforma Android multifuncional integrada

Aplicação Kotlin + Jetpack Compose com motores especializados autónomos, coordenados por contratos, políticas de fluxo e um ledger comum. O núcleo HubOps preserva as funcionalidades originais e incorpora agora VislumbOps como módulo interno.

| Módulo | Inspirado em | O que faz |
|---|---|---|
| GitHub | GitHub_codigo.zip | Pesquisa e pagina repositórios públicos, consulta repos de utilizadores e, com token, os repositórios autorizados da conta (incluindo privados); explora branches, commits, releases e issues; mostra estrelas, forks, linguagem e atualização |
| WordPress | WordPress_codigo.zip | Lê posts públicos ou, autenticado, os conteúdos acessíveis à conta; pagina resultados e cria **rascunhos verificados** via WP REST API |
| Terminal | Termux_codigo.zip | Terminal local no UID da app, sem root; timeout e saída limitados, diretório visível e histórico de sessão limpável |
| Dispositivo | Shizuku_codigo.zip | Diagnóstico de bateria, RAM, armazenamento, tempo desde arranque e deteção Termux/Shizuku |
| Despertar (pago) | ideia do utilizador | Pesquisa e audita estruturalmente repositórios, gera relatórios/ATTRIBUTIONS.md, importa e inspeciona APKs, executa JADX/APKTool no Termux, gera projetos Kotlin/Compose e compila APKs debug pelo Gradle instalado no Termux |
| Feed (glue) | — | Funde fontes num feed limitado, com replay de eventos, histórico e ocultação de argumentos de comandos |
| Espelhar-X | — | IDE offline integrada: edição, análise estática incremental, AST partilhada, mapa de dependências, propostas de reparação, pré-verificação de build e transferências governadas com o Despertar |
| VislumbOps | Vislumbrops 1.1.0 | Auditoria APK, integridade, inventário da cadeia de fornecimento, análise de permissões, segredos e dependências, evidências encadeadas, atestação local e relatórios |
| Omega | — | SNSE (roteamento simbólico + runtime LLM plugável), NSC (planeamento de execução isolada com PRoot/Termux sob limites explícitos) e GoPA (receipts Ed25519, Merkle e quórum de auditoria) |

`Antonio_codigo.zip` foi **descartado** (ver docs/INVENTARIO.md).

## Fluxo profissional Pro

O painel **Pro Flow** exporta um snapshot do workspace Espelhar-X, integra diagnóstico AST e auditoria APK VislumbOps, avaliação cruzada, preparação de reparação, aprovação explícita, backup verificado e nova auditoria. A reparação só pode atingir ficheiros existentes dentro do mesmo prefixo de projeto analisado e inclui opção de reversão condicionada à integridade do backup e do destino. Cada caso mantém um diário append-only encadeado por SHA-256; o diário e o snapshot de estado são validados antes de retomar o caso. Uma nova auditoria exige fingerprint de conteúdo diferente, referência de build distinta e APK com SHA-256 diferente. O APK deve ser reconstruído num ambiente de build confiável após cada correção. A ligação snapshot↔APK usa uma referência declarada e não é prova de proveniência de build sem atestação externa.

## Build
Versão atual: **1.29.0**. Requisitos: JDK 17, Android Studio compatível com AGP 8.10.1, Gradle 8.11.1–8.14.3 e Android SDK 34.

    gradle wrapper --gradle-version 8.11.1  # gera wrapper oficial (se necessário)
    ./gradlew testDebugUnitTest             # testes unitários
    ./gradlew assembleDebug                 # APK em app/build/outputs/apk/debug/

Ou abra a pasta no Android Studio e faça Run.

## Estrutura
    app/src/main/java/dev/hubops/
      core/   code (AST partilhada), integration (orquestração, registry de motores, proveniência e ledger), model, net, config, event
      data/   github, wordpress, terminal, device
      despertar/ project forge, APK intake, Termux bridge e análise de projeto
      espelhar/ IDE, AST pipeline, Repair Engine e gestão de workspace
      glue/   FeedAggregator (código cola)
      ui/     MainActivity, HubApp (router), HubViewModel, screens/*
    app/src/test/  testes de serviços, análise, segurança e orquestração

## Testes manuais
1. Config: guardar token/site; reabrir a app e confirmar que persistem.
2. GitHub: pesquisar `language:kotlin stars:>100`; paginar resultados; consultar repositórios de um utilizador; com token, consultar também os repositórios da conta autenticada (incluindo privados conforme permissões); abrir um repositório e explorar branches, commits, releases e issues.
3. WordPress: carregar posts públicos; com Application Password, consultar conteúdos que a conta pode ler; criar rascunho e confirmar no painel WP.
4. Terminal: `echo ola`, `pwd`, `sleep 30` (deve dar "tempo esgotado"); a execução continua restrita ao UID e sandbox Android da app; o histórico é apenas da sessão e pode ser limpo.
5. Dispositivo/Feed: atualizar diagnóstico de bateria, RAM, armazenamento e uptime; confirmar feed e registo; limpar o feed; desligar a rede e verificar a mensagem de erro.
6. VislumbOps: selecionar um APK, executar auditoria, confirmar hashes/achados/atestado quando elegível, exportar o manifesto JSON e verificar o resultado no ledger comum; confirmar que a auditoria não altera o projeto analisado.

## Licenças
O código original HubOps mantém a licença indicada em `LICENSE`. VislumbOps foi incorporado do arquivo fornecido pelo utilizador; o arquivo de origem não continha licença própria. Consultar `NOTICE.md` antes de redistribuir o conjunto.

## Despertar — regras de segurança/legalidade
- Só age após confirmação de autorização; escreve apenas em repositórios onde o token tem permissão de escrita, e sempre num ramo novo (`despertar/atribuicoes`), nunca no principal.
- Repositórios de terceiros são só lidos; a app **não copia nem transforma código alheio**. Cópia literal só é indicada para licenças permissivas, com atribuição.
- Atribuições são sempre gravadas, nunca ocultadas.
- Pagamento: hoje há só um interruptor de desenvolvimento em Config; falta integrar Google Play Billing (precisa de produto no Play Console).
- Termux: precisa de `allow-external-apps=true`, permissão RUN_COMMAND, acesso ao armazenamento partilhado e JADX/APKTool instalados. A app recebe exit code/stdout/stderr quando o Termux devolve o callback; o resultado não certifica a integridade dos ficheiros.

## Despertar — análise técnica ampliada
- `Análise técnica profunda` consulta metadados, árvore Git, sistemas de build, manifestos, commits/releases e até oito ficheiros pequenos para revisão estática heurística.
- `Copiar relatório Markdown` exporta os dados recolhidos para a área de transferência, com aviso explícito de que a revisão heurística não é uma auditoria de segurança.
- A análise de repositórios públicos não exige permissão de escrita no repositório analisado; a gravação de atribuições continua limitada ao repositório do utilizador e a um ramo separado.
- A análise APK continua dependente do Termux, de permissões Android e de ferramentas externas instaladas. O módulo não injeta código, não remove proteções e não recompila APK automaticamente.

## Despertar — auditoria estrutural e análise APK com retorno

A análise de repositórios foi ampliada com dados obtidos pela GitHub REST API: árvore recursiva de ficheiros (quando disponível), contagem aproximada de ficheiros de código/teste, workflows GitHub Actions, sistemas de build, manifestos de dependências, commits recentes e última release. Até oito ficheiros pequenos podem ser revistos por regras estáticas heurísticas (HTTP, WebView, execução de processos, possíveis segredos, TLS e componentes exportados). A árvore pode ser truncada pela API ou indisponível; nesses casos o relatório assinala a limitação. Isto **não** é uma auditoria de segurança completa, revisão linha a linha ou execução do projeto; pode haver falsos positivos e negativos.

Para análise de APK:
1. O utilizador escolhe o APK com o seletor de documentos Android. A app copia-o para `Download/HubOps`, valida a assinatura ZIP inicial, limita o ficheiro a 500 MB e calcula SHA-256.
2. No Termux, executar `termux-setup-storage` e conceder acesso ao armazenamento quando solicitado.
3. Instalar as ferramentas: `pkg update && pkg install jadx apktool`.
4. Em `~/.termux/termux.properties`, definir `allow-external-apps=true` e reiniciar o Termux.
5. Nas definições Android da HubOps, conceder a permissão adicional **Run commands in Termux environment**. O Termux precisa de acesso ao armazenamento partilhado para ler `Download/HubOps`.
6. Escolher JADX ou APKTool e um nome de pasta ainda não utilizado. A app cria `~/despertar/` e não sobrescreve uma pasta de saída existente.

A integração utiliza `RUN_COMMAND` com `PendingIntent` e recebe exit code, stdout, stderr e erros internos do Termux (Termux >= 0.109). A app guarda o último resultado e mostra-o no ecrã Despertar; a captura local é limitada a 12.000 caracteres por fluxo. O código de saída zero indica que o comando terminou sem erro reportado, mas não certifica a completude nem a qualidade da análise. A primeira execução depende da versão do Termux, da permissão Android, de `allow-external-apps` e do acesso a armazenamento. Referência oficial: https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent.

O APK permanece no armazenamento partilhado em `Download/HubOps`; o utilizador pode removê-lo manualmente depois da análise. A app não envia APKs para servidores externos.

## Despertar — APK Lab e desempenho (2026-11-04)
- A importação agora calcula SHA-256 e faz inspeção estrutural em streaming do ZIP/APK, sem extrair nem executar ficheiros. Resume `AndroidManifest.xml`, DEX, bibliotecas nativas, recursos, assets e ficheiros de assinatura indicativos.
- O inspetor tem limites explícitos: 20.000 entradas, 64 MiB descomprimidos por entrada e 256 MiB no total. Quando um limite é atingido, o relatório assinala análise parcial.
- A inspeção não descodifica o manifesto binário, não valida criptograficamente o certificado e não declara que o APK é seguro.
- O ecrã Despertar pode executar diagnóstico do ambiente Termux (Java, JADX, APKTool, aapt, acesso a Downloads/HubOps, diretório gravável e espaço livre) e listar resultados produzidos por JADX/APKTool sem os modificar.
- As operações do ViewModel são serializadas para impedir que ações concorrentes sobrescrevam estados/resultados. O resultado Termux continua persistido localmente e é atualizado pelo callback.
- APKs não são enviados para servidores. A análise local requer Android 10+ para o fluxo MediaStore usado nesta versão.

## Despertar — Project Forge (2026-11-04)

O ecrã Despertar inclui agora um gerador local de projetos Android independentes:
- Recebe nome, package/applicationId e opção explícita de permissão Internet.
- Valida nome e identificadores antes de criar ficheiros.
- Gera ZIP com Gradle Kotlin DSL, Android Gradle Plugin 8.10.1, Kotlin 2.2.21, Compose BOM, Manifest, `MainActivity.kt`, README e `.gitignore`.
- Não consulta rede, não incorpora código de repositórios analisados e não executa o projeto gerado.
- O ZIP fica temporariamente no cache privado da app e é partilhado pelo Android `FileProvider`; o utilizador escolhe onde o guardar.
- A geração corre em `Dispatchers.IO`, não bloqueia a interface e só é disponibilizada quando o Despertar está desbloqueado.

O projeto gerado deve ser aberto no Android Studio com JDK 17 e Android SDK 34. A sincronização/compilação do projeto gerado ainda deve ser validada num ambiente Android Studio/Gradle real; esta sessão verificou a geração ZIP, estrutura dos caminhos, manifesto e validação do package por teste isolado Kotlin.

### Gestão de cache e polling
O Project Forge mantém apenas os dois ZIPs mais recentes no cache privado. O estado Termux é consultado de forma periódica apenas enquanto existe uma execução marcada como pendente; sem trabalho ativo, não há polling contínuo.


## Despertar — Code Intelligence AST (1.9.0)

- Usa Tree-sitter com grammars reais para Kotlin/Kotlin DSL, Java e Python; percorre árvores sintáticas, conta nós, identifica declarações e sinaliza nós de erro/missing.
- O relatório distingue AST sintática de índice lexical e indica explicitamente quando uma grammar ou biblioteca JNI não está disponível.
- Limites: 1.000 fontes, 1 MB por ficheiro e 100.000 nós por ficheiro. Ficheiros omitidos ou truncados são apresentados no relatório.
- A AST não resolve semântica completa nem referências em runtime. Renomeação/refatoração automática continua desativada até existir resolução semântica e validação de compilação.
- A integração adiciona dependências nativas Android; o build deve ser confirmado com Gradle 8.11.1–8.14.3, JDK 17 e SDK 34.

## Despertar — Android Builder via Termux (1.2.0)
O botão **Compilar projeto e gerar APK no Termux** liga o Project Forge ao compilador real do dispositivo:
1. Gera o ZIP Kotlin/Compose no armazenamento privado da app.
2. Exporta uma cópia por streaming para `Download/HubOps/Projects`, com limite de 50 MB e SHA-256, que é revalidado no Termux antes da extração.
3. Envia um job allowlisted ao Termux.
4. O script valida o caminho do ZIP e rejeita entradas ZIP com caminhos absolutos, segmentos `..` ou symlinks, limita o arquivo a 500 entradas/150 MiB descomprimidos; também exige uma única pasta raiz e os ficheiros Gradle esperados.
5. Verifica `unzip`, JDK 17 e Gradle 8.11.1–8.14.3. Executa `:app:assembleDebug` com no máximo dois workers e heap limitado a 1,5 GB.
6. Copia o APK debug para `Download/HubOps/Builds/<id>-debug.apk` sem substituir ficheiros existentes.

### Preparação no Termux
- `termux-setup-storage` e conceder a permissão solicitada.
- Instalar `unzip`, JDK 17, Gradle 8.11.1–8.14.3 e Android SDK Platform 34 + Build Tools 34.0.0.
- Confirmar que `ANDROID_HOME` aponta para o SDK e que `sdkmanager` instalou a plataforma e build-tools.
- Em `~/.termux/termux.properties`, manter `allow-external-apps=true`, reiniciar o Termux e conceder a permissão Android de execução de comandos.

A primeira compilação requer rede para transferir dependências Gradle/Android. O processo pode demorar e consumir bastante armazenamento. O HubOps não afirma que o APK está pronto até receber `exitCode=0`; mesmo assim, o utilizador deve confirmar a existência/tamanho do artefacto. A instalação do APK permanece manual nesta versão.


## Despertar Builder 1.3.0

A geração de projetos Android e a orquestração de builds no Termux agora incluem validação pós-build do APK: integridade ZIP, manifesto, DEX, correspondência do applicationId via aapt, verificação de assinatura via apksigner e relatório SHA-256. A interface só mostra o artefacto após o callback de sucesso e o marcador `BUILD_OK`. Consulte `docs/ANDROID_BUILDER.md` para requisitos e códigos de erro.


## Despertar — Repair Engine diagnóstico (1.4.0)

Quando uma execução Termux termina, o HubOps analisa stdout/stderr com regras locais determinísticas e apresenta até oito diagnósticos deduplicados, com evidência curta, confiança e passos de investigação. Abrange falhas frequentes de SDK, JDK, Gradle/AGP, resolução de dependências, símbolos Kotlin/Java, classes duplicadas, Manifest Merger, memória, armazenamento, metadados Kotlin e permissões. O relatório pode ser copiado para partilha ou arquivo.

O script de compilação emite `BUILD_FAILED` e `GRADLE_EXIT` se Gradle falhar. O Repair Engine não edita ficheiros nem executa correções automáticas: é deliberadamente consultivo para evitar alterações inseguras ou destrutivas. Os logs são truncados para os últimos 200.000 caracteres e o painel limita-se a oito diagnósticos. A classificação é heurística e não substitui a leitura do log completo nem a validação após uma correção. Foram executados smoke tests Kotlin isolados; a compilação Android integral e os testes no dispositivo continuam por confirmar.


## Code Intelligence (1.5.0)

Depois de gerar um projeto Android ZIP, use **Analisar projeto · Code Intelligence** para obter uma inspeção local da estrutura. O relatório contabiliza fontes Kotlin/Java e testes, identifica TODO/FIXME e ficheiros extensos, e verifica sinais básicos de manifesto e configuração Gradle. A leitura é limitada (2.000 entradas, 1.000 ficheiros relevantes, 2 MB por entrada e 20 MB no total), não extrai os ficheiros nem executa scripts. Os resultados são heurísticos: não substituem compilação real, testes automatizados ou revisão de segurança.


## Project Architect (1.6.0)

Depois da Code Intelligence, **Arquitetar projeto · Project Architect** identifica módulos Gradle declarados em settings, packages Kotlin/Java, imports internos/externos e dependências inferidas entre packages. Sinaliza dependências circulares prováveis e packages com elevada saída de dependências, e produz um plano de implementação em etapas. A análise é somente leitura, reutiliza a leitura limitada do ZIP e não executa código nem modifica ficheiros. A inferência é baseada em expressões regulares, não numa AST completa; imports com aliases, geração de código, reflexão e dependências dinâmicas podem não ser representados. O plano é uma recomendação para revisão humana, não uma alteração automática.


## Despertar — Component Fusion (1.7.0)

Use **Selecionar ZIP de referência** depois de gerar o projeto principal. O HubOps compara os dois ZIPs localmente e reporta caminhos de fontes coincidentes, nomes de ficheiros repetidos, packages partilhados, possíveis versões divergentes de dependências Gradle e diferenças de permissões no Manifest. Em seguida, apresenta um plano de integração que exige revisão humana e testes.

O ZIP de referência é limitado a 30 MB comprimidos; a leitura interna mantém os limites da Code Intelligence (2.000 entradas, 1.000 ficheiros relevantes, 2 MB por entrada e 20 MB descomprimidos). Nenhum conteúdo é extraído para armazenamento, executado ou fundido automaticamente. O relatório não certifica compatibilidade: recursos, APIs, manifest merger, plugins, NDK, licenças e comportamento devem ser revistos antes de integrar.

## Espelhar-X integrado (1.12.0)

O Espelhar-X é um módulo Kotlin/Compose nativo, sem segunda aplicação, servidor HTTP local ou runtime paralelo. Recebe fontes do ZIP gerado pelo Despertar com limites e validação contra Zip Slip, preservando ficheiros já existentes; mantém editor, análise incremental, símbolos, pipeline e execução explícita de Shell pelo terminal privado. O pipeline combina análise heurística com AST Tree-sitter real para Kotlin, Kotlin DSL, Java e Python, reportando indisponibilidade de grammar/JNI sem a confundir com sucesso. A AST é sintática, não uma análise semântica completa. A gravação usa ficheiro temporário e movimento atómico quando suportado. O ShellRunner usa o UID da aplicação, não proot nem isolamento de contentor.


## Espelhar-X 1.15.0

O Espelhar-X integra editor/análise local, pipeline AST Tree-sitter (quando grammars/JNI estão disponíveis), mapa conservador de imports com associação por package e declaração, comparação somente-leitura de snapshot GitHub com cópia local, propostas de normalização com hash e backup, importação de projetos do Despertar, snapshot GitHub somente-leitura e pré-verificação de build Android. A pré-verificação verifica manifesto, ficheiros Gradle, fontes e completude do Wrapper; não executa Gradle nem certifica a geração de APK. O snapshot GitHub é limitado a 40 ficheiros e 10 MB e nunca faz push. A comparação local classifica novos/alterados/iguais/apenas locais, sem modificar o workspace. A resolução de dependências continua conservadora e não substitui o compilador. A comparação GitHub classifica ficheiros novos, alterados, iguais e apenas locais sem modificar o workspace. Consulte `docs/VALIDACAO_1.15.0.md` para o estado real dos testes e limitações.


## Espelhar-X 1.16.0 — Repair Rollback
O Repair Engine agora suporta reversão verificada de uma proposta aplicada: exige que o ficheiro atual ainda corresponda exatamente ao conteúdo proposto e que o backup corresponda ao SHA-256 original. Restaura por ficheiro temporário e movimento atómico quando suportado, e arquiva o backup de auditoria. A interface mantém a lista de reparações aplicadas durante a sessão e oferece reversão explícita. Se o ficheiro tiver sido alterado depois da reparação, a reversão é recusada para não apagar trabalho posterior. A lista de ações aplicadas é mantida em memória nesta versão; o backup permanece no workspace após fechar a app.

Consulte também `docs/VALIDACAO_1.16.0.md` para o estado da reversão de reparações e os limites ainda não validados.


### Motor independente de verificação de assinatura APK (1.19.0)

O `ApkSignatureVerificationEngine` utiliza o parser `PackageManager` do Android para obter os certificados de assinatura do APK, calcular fingerprints SHA-256 e registar package name, quantidade de signatários e disponibilidade do histórico de rotação. A ausência de certificado é tratada como erro, nunca como aprovação. Esta verificação é feita pela plataforma Android; não identifica de forma independente cada esquema v1/v2/v3/v4 nem substitui uma auditoria especializada com apksig.


## Motor de Recuperação Transacional e Planeamento Assistido (1.22.0)
O HubOps mantém um diário durável das transferências governadas. Após reinício, operações não terminais são classificadas como `NEEDS_ATTENTION` para reconciliação explícita. O motor é deliberadamente conservador: não elimina staging, não substitui ficheiros do utilizador e não executa rollback ou commit automaticamente. A integração pode ser consultada por `inspectInterruptedTransactions()` e `recentTransactions()`. `reconcileInterruptedTransactions(workspace)` cruza as evidências sem mutações; `planRecovery(workspace)` produz uma sequência recomendada de inspeção, validação e decisão humana, identificada por SHA-256, sem executar qualquer ação.


## Controlled repair (1.23.0)
`ControlledRepairEngine` prepares single-file repairs inside an explicitly selected workspace, binds approval to SHA-256 preconditions, preserves verified backups, and supports guarded rollback. Preparation is read-only; application requires the exact approval token and rechecks the destination and replacement hashes. Newly created files are never deleted automatically during rollback.


## Fluxo operacional Pro (1.25.0)

A rota **Pro Flow** conduz o ciclo completo: exportar um snapshot do projeto no workspace Espelhar-X e analisar AST; auditar o APK com VislumbOps; consolidar evidências com hashes independentes; preparar uma correção de ficheiro único no workspace Espelhar-X; aprovar explicitamente; aplicar com backup; reconstruir fora do HubOps; e auditar novamente o novo APK e snapshot. O diário do caso é persistente, append-only e encadeado por SHA-256, com verificação de integridade antes da retoma. Uma nova auditoria exige conteúdo de fonte diferente no mesmo workspace, referência de build diferente e APK com SHA-256 diferente; o fingerprint ignora timestamps e ordem do ZIP.

A relação entre fonte e binário continua declarativa: uma referência comum não prova que o APK foi produzido a partir do snapshot. Para certificação de proveniência é necessária atestação assinada pelo ambiente de build.

### Utilização

1. Importe o projeto para o workspace Espelhar-X (por GitHub, Despertar ou fontes locais) e confirme a pasta do projeto.
2. Abra **Pro Flow**, indique o prefixo relativo (por exemplo `projects/minha-app`) e uma referência única da build; execute **Criar caso e analisar workspace**.
3. Selecione o APK correspondente e aguarde a auditoria VislumbOps. Só então consolide a avaliação cruzada.
4. Para corrigir, prepare um ficheiro revisto cujo destino já exista dentro do mesmo prefixo. Reveja os hashes e aprove explicitamente; a aplicação cria backup. A reversão só ocorre se o destino e o backup ainda passarem as verificações.
5. Reconstrua a aplicação fora do HubOps. Atualize a referência de build, exporte o novo snapshot do mesmo workspace, selecione o novo APK e registe a reauditoria. O mesmo hash binário é recusado.


## Avaliação cruzada AST + VislumbOps (1.24.0)
- `HubOpsOrchestrator.assessProjectAcrossEngines` combina, em modo somente leitura, a análise AST do ZIP-fonte com um contrato VislumbOps já concluído para o APK.
- A ligação exige `buildReference` explícita e preserva SHA-256 separados para o ZIP-fonte e o APK; não presume que um artefacto foi produzido a partir do outro.
- Achados AST sintáticos e heurísticos são normalizados para o modelo de findings VislumbOps; a cobertura AST parcial é reportada como aviso.
- A avaliação gera hash SHA-256 de evidência agregada e declara limitações. Não aplica correções, não promove artefactos e não emite certificado novo.


## HubOps 1.26.0 — integridade de commit e proveniência CI

O diário do Pro Flow usa agora um registo write-ahead (`.pending`) para concluir de forma idempotente a escrita do evento e do snapshot se o processo terminar entre as duas operações. O arranque reconcilia o registo pendente, verifica o hash e não duplica eventos. A avaliação cruzada também deixa de apresentar a referência manual de build como prova de proveniência: cobertura AST incompleta e proveniência não verificada obrigam a revisão.

O workflow `.github/workflows/android-ci.yml` executa testes JVM, lint Android e assembleDebug em runner GitHub, gera SHA-256/metadados e atesta o APK debug com GitHub build provenance. A atestação ainda deve ser verificada externamente com GitHub CLI; a app não valida ainda a bundle Sigstore/in-toto. Consulte `docs/BUILD_ATTESTATION.md` e `docs/VALIDACAO_1.26.0.md`.

## Omega — SNSE, NSC e GoPA (1.27.0)

A rota **Omega** reúne três submotores integrados ao ciclo de vida da aplicação: SNSE encaminha achados para regras/shards especializados e arbitra propostas sem as aplicar; NSC escolhe entre análise JVM estática, PRoot user-space, delegação Termux ou indisponibilidade; GoPA verifica receipts Ed25519 e quóruns de auditoria sobre um conjunto explícito de peers. O acesso programático é feito por `HubOpsApplication.omegaEngines`.

Esta distribuição inclui as interfaces e os mecanismos determinísticos, mas não contém pesos GGUF 1B, runtime llama.cpp, binário proot, rootfs nem transporte de rede gossip. Assim, o SNSE inicia em fallback simbólico, NSC não declara contentores disponíveis sem binário/rootfs testados e GoPA requer provisionamento explícito de chaves públicas. Consulte `docs/OMEGA_ENGINES_1.27.0.md` para arquitetura, limitações e evidências de validação. Não confundir proposta de patch com correção comprovada, PRoot com Docker ou quórum de auditoria com BFT geral.

### Utilizar Omega

1. Abra **Omega**, cole o conteúdo exato do ficheiro e indique um CWE do catálogo suportado. Sem pesos GGUF e runtime local instalado, o resultado será simbólico e não produzirá patch de IA.
2. Quando existir runtime/modelo, reveja a proposta e indique o caminho relativo do ficheiro no mesmo workspace Espelhar-X. O HubOps compara o texto analisado com o ficheiro atual antes de preparar a reparação.
3. Envie a proposta para **Pro Flow**. A aplicação não escreve no workspace nesta etapa: a aprovação explícita, backup e aplicação continuam no painel Pro Flow.
4. Em NSC, use **Avaliar JVM/UNIX** para consultar capacidades. A execução PRoot só fica disponível após provisionar binário ELF e rootfs privados; PRoot não é Docker nem sandbox de kernel.
5. GoPA é uma API para integradores: configure um conjunto de peers confiáveis e chaves Ed25519 fora da UI. A rede gossip, armazenamento durável e ciclo de vida das chaves ainda não estão incluídos.

## Omega 1.28.0 — SNSE, NSC e GoPA com preparação de runtime

A versão 1.28.0 acrescenta a preparação de inferência llama.cpp para Android arm64, importação individual de modelos GGUF assinados, bootstrap explícito de PRoot/Alpine com manifesto assinado e um servidor GoPA v2 compatível com o cliente Android (Ed25519, TLS, quórum sobre peers elegíveis e ledger persistente). O APK não inclui os sete modelos: são distribuídos separadamente em `dist/snse-models-arm64.zip` e importados individualmente para evitar um APK de vários gigabytes.

A integração não equivale ainda a uma release validada em dispositivo. O runtime llama-cli precisa ser testado em Android real; a compilação PRoot/Bionic pode exigir ajustes upstream; a versão Alpine empacotada (3.24.x, conforme `docs/OMEGA_1.28.0_IMPLEMENTACAO.md`) deve ser revalidada contra o canal suportado antes de produção; os testes de precisão, recall, RAM, latência e estabilidade térmica não foram executados nesta sessão. Consulte `docs/OMEGA_1.28.0_IMPLEMENTACAO.md` e `docs/VALIDACAO_1.28.0.md` para o procedimento e estado exato.

## 1.29.0 — Auditor de dependências corrigido e verificações de qualidade

- **Correção:** a correspondência de versões do `DependencyAuditor` usava prefixos de texto (`"4.1"` casava `4.10`; `"2.14"` ignorava 2.1–2.13). Passou a comparar versões numericamente em intervalos `[introduced, fixed)` (`CveCatalog.kt`, JVM puro, com suporte a sufixo de letra estilo OpenSSL).
- **Correção de dados:** Heartbleed marcava OpenSSL 1.0.x/1.1.0 por engano (afeta só 1.0.1–1.0.1f). A entrada OkHttp CVE-2023-0833 foi removida por mapeamento de versões não confirmado; não reintroduzir sem verificar no NVD.
- **Ampliação:** base local de 11 CVEs com intervalos; versão lida de `META-INF/maven/**/pom.properties` (confiança alta) além da inferência por nome; achados INFO deduplicados; 7 novos padrões de segredos; `network_security_config` agora referenciada no Manifest.
- **Qualidade sem Android SDK:** `tools/quality/check_kotlin_static.py` (imports internos e declarações duplicadas) e `tools/quality/validate_cve_db.py` (esquema da base + 15 vetores de versão). Ambos correm no CI.
- Estado real de validação em `docs/VALIDACAO_1.29.0.md`.
