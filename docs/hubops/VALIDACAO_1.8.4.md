# Validação técnica — HubOps Despertar 1.8.4

## Implementado

- Índice lexical limitado de declarações nomeadas (`class`, `interface`, `object`, `record`, `fun`, `def`, `typealias`) em Kotlin, Kotlin DSL, Java e Python.
- Contagem de ocorrências de identificadores declarados noutros locais, com caminhos e linhas das declarações.
- Comentários de linha/bloco e literais simples/triplos são ignorados para reduzir falsos positivos.
- Nomes com múltiplas declarações são marcados como ambíguos; o sistema não tenta escolher um alvo sem escopo/tipo.
- O índice aparece no ecrã Code Intelligence e no relatório Markdown.
- Scripts Gradle `.kts` continuam disponíveis para análise lexical, mas deixaram de inflacionar a contagem de ficheiros de código-fonte Kotlin.

## Limites explícitos

- Isto é indexação lexical, não AST nem resolução semântica. Não determina escopos, overloads, tipos, dispatch, aliases, reflection ou imports com precisão de compilador.
- Não há renomeação automática nem edição de ficheiros nesta versão.
- O índice está limitado a 1.000 ficheiros, 1 MB e 100.000 tokens por ficheiro; símbolos exibidos limitados a 100.
- Não foi executado build Android integral sem Gradle/Android SDK e wrapper no ambiente.

## Verificações executadas

- Compilação isolada de `ProjectSymbolIndex.kt` e `ProjectIntelligence.kt` com `kotlinc`: aprovada.
- 9 testes de `ProjectIntelligence`: aprovados com runner local compatível com as asserções JUnit usadas.
- 13 testes de `CodeSimilarityEngine`: aprovados com runner local compatível com as asserções JUnit usadas.
- Build Android integral e suite Gradle oficial: não executados, por ausência de Gradle, Android SDK e wrapper.

## Testes adicionados

- Referências entre ficheiros e exclusão de comentários/literais.
- Declarações duplicadas sinalizadas como ambíguas.
- Reconhecimento de declarações Java e Python.
