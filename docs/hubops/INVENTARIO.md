# A) Inventário e análise

| ZIP | Conteúdo | Tecnologia | Ideias aproveitadas | Riscos |
|---|---|---|---|---|
| GitHub_codigo.zip | jadx de um app GitHub (14 711 ficheiros `defpackage/aXX.java`) | Java descompilado, ofuscado | Navegação de repositórios | Código ofuscado e proprietário; nada reutilizável |
| Termux_codigo.zip | apktool do Termux (1 591 ficheiros, só recursos) | smali/res | Consola de comandos | GPLv3; sem código-fonte legível |
| WordPress_codigo.zip | apktool do WordPress 27.6 (72 287 ficheiros, `org.wordpress.android`) | smali multi-dex, Kotlin | Listar/criar posts via REST | GPLv2+; enorme; dependências de terceiros |
| Shizuku_codigo.zip | apktool do Shizuku 13.6 (`moe.shizuku.privileged.api`) | smali, `rikka.*` | Deteção de serviço privilegiado | Apache 2.0; execução privilegiada exige binder/ADB |
| Antonio_codigo.zip | apktool de "Antônio" (pacote `ru.aaaaabap.installer`, renomeado `ru.dgiwauco.wyifewayh`, nomes unicode ofuscados) | smali ofuscado | **Nenhuma (descartado)** | Pede ACCESS_SUPERUSER, SYSTEM_ALERT_WINDOW, QUERY_ALL_PACKAGES, REQUEST_INSTALL_PACKAGES, MANAGE_EXTERNAL_STORAGE, Ad ID, boot; ofuscação e reempacotamento: perfil típico de adware/app modificada |

# B) Especificação
Nome HubOps; público: devs/admins que gerem repositórios, sites WordPress e shell no telemóvel. MVP: GitHub, WordPress, Terminal, Config cifrada, Feed. Extra: diagnóstico do dispositivo. minSdk 26, targetSdk 34.
Camadas: core (modelos/adapters/rede/config/eventos) → data (serviços) → glue (FeedAggregator) → ui (Compose, router por enum).

# Aplicação dos métodos
1. Arquitetura e isolamento: um pacote por origem conceptual (`data/github`, `data/wordpress`, `data/terminal`, `data/device`), nada partilhado além de `core`.
2. Núcleo de dados: `Models.kt` + `Adapters.kt` (RepoDto/PostDto/CommandResult → FeedItem).
3. Lógica/serviços: `GitHubService`, `WordPressService`, `ShellRunner`, com validação de entradas.
4. UI híbrida: `HubApp` (router) + ecrãs com um só tema.
5. Glue + testes: `EventBus` + `FeedAggregator`; testes unitários de parsers, validação e shell.

# Limitações conhecidas
- **Não compilado nem testado aqui** (sem Gradle/Android SDK/rede no ambiente). Pode haver erros de compilação a corrigir no Android Studio.
- Execução privilegiada (Shizuku) **não** implementada: só deteção. Integrar exige a Shizuku-API (Apache 2.0) e testes em dispositivo.
- Terminal corre com os privilégios da própria app (não é o Termux).
- Sem ícone próprio nem Gradle wrapper (gerar com `gradle wrapper`).
- `security-crypto` 1.1.0-alpha06 é versão alfa.
# Próximos passos
Ícone, paginação, publicar posts, ViewModel testável com coroutines-test, integração Shizuku opcional.

# Atualização — expansão do Despertar (04-11-2026)
- Acrescentado estudo técnico de repositórios GitHub: metadados, ramo padrão, estado arquivado, métricas públicas, licença SPDX, tópicos, linguagens e excerto limitado do README.
- Acrescentada exportação de relatório Markdown para a área de transferência, com delimitação explícita do âmbito (não é auditoria de segurança).
- Ampliada a classificação de licenças comuns (MPL 1.1, EPL 1.0, CDDL e variantes GPL/AGPL adicionais).
- Mantida a separação entre leitura de projetos públicos e escrita no repositório do utilizador. Nenhuma cópia de código foi adicionada.
- Testes unitários adicionais para relatório técnico e famílias de licenças.

### 1.8.1 — Code Similarity & Refactoring Lab
- `CodeSimilarityEngine.kt`: comparação lexical/estrutural limitada para Kotlin, Kotlin DSL, Java e Python; métricas por caminho coincidente, normalização de identificadores/literais e relatório Markdown.
- `HubViewModel.compareCodeSimilarity()`: execução serializada em `Dispatchers.IO`, reutilizando leitura ZIP segura.
- `DespertarScreen`: ação e relatório integrados à área Component Fusion.
- `CodeSimilarityEngineTest.kt`: igualdade, normalização estrutural, reconhecimento multilíngue e caminhos não emparelhados.
- Refatoração AST ainda não executa transformações: exige parser e resolução de símbolos por linguagem. O estado é explicitado na interface e no relatório para evitar alegações de segurança não verificadas.
