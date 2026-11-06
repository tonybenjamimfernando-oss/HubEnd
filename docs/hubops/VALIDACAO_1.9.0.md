# Validação técnica — HubOps Despertar 1.9.0

## Implementado nesta versão

- Integração do runtime Tree-sitter para Android e grammars Kotlin/Kotlin DSL, Java e Python via dependências Maven Central.
- Análise de árvores sintáticas concretas: contagem de nós, deteção de nós de erro/missing, controlo de truncagem, extração de declarações nomeadas e linhas.
- Limites de 1.000 ficheiros, 1 MB por fonte e 100.000 nós por ficheiro.
- Falhas de dependência/JNI/API são reportadas como ficheiros não analisados; nunca são contadas como sucesso.
- Relatório AST e índice lexical permanecem explicitamente separados.
- Deteção de Gradle Wrapper exige os três elementos: `gradlew`, `gradle-wrapper.jar` e `gradle-wrapper.properties`.
- Atualização da toolchain: AGP 8.10.1, Kotlin 2.2.21, Gradle 8.11.1, JDK 17 e SDK 34.

## Dependências de parsing

- `io.github.tree-sitter:ktreesitter-android:0.25.1`
- `cn.enaium.treesitter:treesitter-languages-kotlin-kmp-android:0.3.8.2`
- `cn.enaium.treesitter:treesitter-languages-java-kmp-android:0.23.5.2`
- `cn.enaium.treesitter:treesitter-languages-python-kmp-android:0.25.0.2`

## Limites que permanecem

- Tree-sitter fornece AST sintática, não análise semântica completa. Não resolve overloads, tipos, dispatch, reflection, macros ou referências dinâmicas.
- Renomeação/refatoração automática permanece desativada para evitar alterações incorretas em símbolos homónimos ou APIs públicas.
- AST depende da resolução correta dos AARs e bibliotecas nativas no build Android. O ambiente desta execução não tem acesso DNS/rede para descarregar dependências Maven, Gradle ou Android SDK; portanto a integração de runtime não pôde ser compilada/executada integralmente aqui.
- O projeto ainda não inclui o `gradlew`, `gradlew.bat` e `gradle-wrapper.jar` oficiais. `gradle-wrapper.properties` sozinho não é um wrapper executável. A compilação no Termux usa Gradle instalado.

## Verificações executadas

- Compilação Kotlin isolada de `TreeSitterAstAnalyzer.kt`: aprovada.
- Compilação isolada dos módulos `ProjectSymbolIndex`, `ProjectIntelligence`, `ProjectArchitect`, `CodeSimilarityEngine` e `AndroidProjectGenerator`: aprovada.
- 11 testes Project Intelligence: aprovados.
- 13 testes Code Similarity: aprovados.
- 2 testes Android Project Generator: aprovados.
- Total: 26 testes executados com um runner local compatível com as asserções JUnit usadas; não é a suite oficial Gradle/JUnit.
- Smoke test do adaptador reflexivo com classes Tree-sitter simuladas: aprovado; valida o contrato de reflexão, não a execução das grammars nativas reais.
- Integridade do ZIP final: validada após empacotamento.

## Não executado / bloqueios externos

- Build Android/Gradle oficial (`assembleDebug`) e testes Android oficiais: não executados. O ambiente não tem Gradle instalado, Android SDK/NDK configurados, wrapper JAR ou acesso DNS para resolver os AARs Maven/Google.
- A execução das grammars Tree-sitter reais e JNI no dispositivo Android ainda precisa de validação num build com rede e SDK/NDK.

## Referências técnicas das dependências

- KTreeSitter documenta suporte Android e o contrato `Language` → `Parser` → `parse` → `Tree.rootNode`: https://tree-sitter.github.io/kotlin-tree-sitter/
- Catálogo de grammars KMP documenta Java, Python e Kotlin, os respetivos números de versão e os artefactos Android/JNI: https://klibs.io/project/Enaium/tree-sitter-languages-kmp
