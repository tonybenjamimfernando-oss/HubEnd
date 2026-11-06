# HubOps 1.10.0 — integração do Espelhar-X

## Decisão de arquitetura

O Espelhar-X foi integrado como módulo Kotlin/Jetpack Compose no próprio HubOps. Não foi criada uma segunda aplicação nem foram adicionados Vue, Vite, Capacitor, servidor HTTP local ou um runtime Python paralelo. O módulo usa o workspace privado da aplicação e reutiliza o `ShellRunner` existente para execução explícita de scripts Shell.

## Funcionalidades implementadas

- Editor de texto com caminho relativo, abertura e gravação no workspace privado `filesDir/espelhar-x`.
- Análise estática local com debounce de 450 ms após a edição e botão de análise manual, com cache SHA-256 por caminho/conteúdo.
- Extração heurística de classes/funções para Python, Kotlin, Java e JavaScript/TypeScript.
- Deteção inicial de delimitadores desequilibrados, `except:` sem tipo, defaults mutáveis e possível código morto em Python; validação básica de JSON/XML.
- Pipeline que percorre até 200 ficheiros suportados no workspace e apresenta estado por ficheiro, sem executar nem modificar os ficheiros analisados.
- Execução de scripts Shell apenas após ação explícita, através do `ShellRunner` já existente (timeout e limite de saída mantidos).
- Nova rota Espelhar-X no menu principal.

## Preservação

- O pacote `app/src/main/java/dev/hubops/despertar/` foi comparado com a versão 1.9.2; deve permanecer byte-a-byte inalterado.
- GitHub, WordPress, Feed, Terminal, Configurações e Despertar permanecem módulos separados.
- Nenhuma credencial adicional ou permissão Android foi introduzida.

## Auditoria do código Espelhar-X recebido

O código original não foi incorporado literalmente porque contém pontos que impediriam uma integração segura/confiável: erro de indentação em `omni_parser.py`; análise de variáveis indefinidas com falsos positivos por depender da ordem de `ast.walk`; scanner de delimitadores que não ignora strings/comentários; cache baseado no `hash()` não persistente do Python; modo `subprocess` apresentado como sandbox embora não isole processos; invocação de comandos por concatenação sem quoting robusto; servidor HTTP com CORS `*`, sem autenticação e sem limite de corpo; fallback frontend mock que apresenta execuções simuladas como sucesso; e integração Android com Chaquopy sem configuração Gradle/dependência correspondente no projeto fornecido. A alegação de latência fixa de 0,47 ms e de isolamento proot não é demonstrada pelo código.

## Limitações explícitas da integração

- O analisador é heurístico, não um parser/compilador completo. A indicação “sem problemas” significa apenas que estas verificações limitadas não encontraram ocorrências.
- Não há execução de Python, Rust/WASM, JavaScript, Kotlin ou Dart no novo editor. Apenas Shell pode ser executado, de forma explícita.
- ShellRunner executa sob o UID Android da própria aplicação. Não é proot, container, VM ou sandbox de segurança forte. O aviso é apresentado na UI.
- O pipeline verifica ficheiros no workspace Espelhar-X; não substitui CI remoto (GitHub Actions) nem o pipeline de build do Despertar.
- A compilação Android completa e os testes Gradle não foram executados neste ambiente: não há Gradle/wrapper executável nem Android SDK configurado. A compilação Compose da nova UI permanece por confirmar num ambiente Android válido.

## Validações efetuadas neste ambiente

- `EspelharEngine.kt` compilado isoladamente com `kotlinc`, usando um stub temporário de `JSONTokener` apenas para contornar a ausência do SDK Android no ambiente de validação.
- Smoke tests executados: deteção de default mutável, extração de símbolo, cache incremental, rejeição de path traversal e deteção de delimitador incompatível — todos passaram.
- Testes JUnit adicionados ao projeto: seis casos para análise, cache, strings/comentários, delimitadores, gravação e path traversal. A execução Gradle destes testes ainda está pendente.
- A verificação completa de Android/Compose e geração de APK ainda não foi possível.
