# Validação HubOps 1.13.0 — Espelhar-X Project Intelligence

## Alterações implementadas

- Mapa de dependências estático: associa imports a packages/declarações encontradas no conjunto de fontes e classifica relações como associadas, não resolvidas ou ambíguas.
- Repair Engine conservador: propõe apenas remoção de BOM UTF-8, normalização de espaços/finais de linha e newline final. Não inventa correções semânticas.
- Aplicação de proposta com hash SHA-256 do conteúdo original, rejeição de ficheiros alterados desde a análise, cópia `.hubops-backup` e substituição temporária.
- GitHub Bridge somente-leitura: lê branch/ref e árvore Git, filtra ficheiros textuais suportados, limita a 40 ficheiros e 10 MB, rejeita conteúdo binário/caminhos ocultos/inseguros e importa para pasta isolada `github-owner-repo/`.
- Integração do mapa e das propostas no pipeline e no ecrã Espelhar-X.
- Preservação dos fluxos existentes de Despertar, Terminal, WordPress, Feed e GitHub.

## Testes adicionados

- Associação de imports e deteção de imports não resolvidos.
- Propostas determinísticas de normalização.
- Aplicação de reparação com backup.
- Recusa de reparação se o ficheiro mudou após a análise.
- Importação de snapshot GitHub sem sobrescrita.

## Validação executada neste ambiente

- Compilação isolada de `EspelharProjectServices.kt` com `kotlinc`: passou.
- Exercício manual da associação de imports e geração de propostas via Kotlin script: passou.
- Build Android/Gradle e suite JUnit: não executados. O ambiente não tem Gradle instalado nem `gradlew`/`gradle-wrapper.jar` no repositório. Não declarar a aplicação Android compilada ou os testes Android aprovados.
- Integração nativa Tree-sitter/JNI em dispositivo Android: ainda requer validação num dispositivo/emulador com ABI suportada.
- GitHub Bridge: implementação local concluída, mas chamada real à API requer token/rede e não foi executada neste ambiente.

## Limitações conhecidas

- O mapa de imports é uma associação estática conservadora; não implementa resolução semântica completa de Kotlin/Java/Python nem substitui Gradle/compiler.
- O Repair Engine só aplica normalizações de texto determinísticas. Não altera lógica, nomes de símbolos ou dependências automaticamente.
- Snapshot GitHub é leitura/importação local. Não cria commits, branches nem faz push.
- A validação definitiva de APK exige Gradle Wrapper oficial completo, Android SDK, dependências nativas Tree-sitter e testes em dispositivo/emulador.
