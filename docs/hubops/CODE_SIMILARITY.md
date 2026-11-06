# Code Similarity & Refactoring Lab

## Estado na versão 1.8.2

O laboratório está integrado no fluxo Despertar > Component Fusion. A comparação é somente de leitura: não extrai entradas para o armazenamento, não executa fontes e não modifica os ZIPs.

### Métricas

- **Similaridade textual:** Dice sobre trigramas ordenados de tokens, preservando literais e operadores e removendo comentários/espaços. A ordem das operações influencia a pontuação.
- **Similaridade estrutural lexical:** a mesma comparação depois de abstrair identificadores e literais. É um indicador de forma lexical, não uma AST nem uma medida de equivalência semântica.
- **Agregação do projeto:** soma das percentagens dos pares dividida pelo maior inventário de fontes entre os projetos. Fontes sem par reduzem a pontuação global.
- **Emparelhamento em duas fases:** primeiro caminho relativo exato; depois procura heurística de ficheiros movidos/renomeados, comparando fontes da mesma linguagem. Um par heurístico requer pelo menos 58% de similaridade estrutural lexical e pelo menos 8 tokens em cada ficheiro, para evitar associar ficheiros triviais apenas por terem a mesma forma. Os pares são apresentados com ambos os caminhos e identificados como heurísticos.

### Linguagens reconhecidas

Python (`.py`), Kotlin (`.kt`), Kotlin DSL (`.kts`) e Java (`.java`). A tokenização trata comentários de linha/bloco, comentários de bloco aninhados Kotlin, strings multilinha Python/Kotlin/Java e operadores comuns. Os segmentos de caminho são preservados; não se remove automaticamente a primeira pasta, pois ela pode ser um módulo legítimo. Isto não equivale a análise sintática completa.

### Limites de execução

- Até 1.000 fontes por projeto.
- Até 1 MB de texto por fonte na comparação.
- Até 100.000 tokens por fonte.
- Até 20.000 comparações candidatas para procurar ficheiros movidos/renomeados.
- A leitura de ZIP usada pelo Code Intelligence limita a 2.000 entradas, 2 MB por entrada e 20 MB descompactados no total.

O relatório informa quantas fontes foram omitidas, quantos ficheiros foram truncados e se a procura de pares atingiu o limite. Estes limites protegem memória e latência em dispositivos móveis.

### Refatoração AST

A refatoração continua deliberadamente desativada. Uma implementação segura exige parsers reais por linguagem, resolução de símbolos/escopos, tratamento de imports e referências, escrita não destrutiva e validação sintática + testes de regressão. Não se usa regex nem renomeação isolada de declarações. O relatório informa explicitamente que nenhuma refatoração foi executada.

### Próxima arquitetura recomendada

1. Interface `LanguageAnalyzer` com detecção de linguagem e capacidades declaradas.
2. Adaptador Python baseado no parser `ast` da versão de Python executada no ambiente de análise.
3. Adaptador Kotlin baseado em PSI/compiler analysis compatível com a versão Kotlin do projeto.
4. Adaptador Java baseado em parser Java com resolução de símbolos quando classpath estiver disponível.
5. Plano de alterações (dry-run), diff, verificação de referências, parse pós-alteração e execução de testes antes da exportação.

Nenhum adaptador deve declarar suporte de refatoração se não conseguir resolver referências e validar a árvore resultante.

## Evolução 1.8.3
A auditoria estática complementar passou a apresentar caminho e linha para cada padrão encontrado e deixou de concatenar fontes de ficheiros diferentes antes da procura de sinais contextuais. Isto melhora a rastreabilidade e reduz falsos positivos entre ficheiros, sem transformar as regras heurísticas em análise semântica ou AST.
