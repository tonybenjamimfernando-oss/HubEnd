# Atribuições e dependências

O código original de HubOps foi preservado. O motor VislumbOps foi incorporado a partir do arquivo Vislumbrops 1.1.0 fornecido pelo utilizador, mantendo o seu namespace e a sua estrutura de módulos. O arquivo de origem não continha um ficheiro LICENSE; antes de redistribuição pública, deve ser confirmada a titularidade/licença dos componentes importados.
Projetos que inspiraram ideias, com as respetivas licenças, caso queira reutilizar código deles no futuro:

- Termux (GPLv3) — ideia de consola de comandos
- Shizuku (Apache 2.0) — ideia de diagnóstico/deteção de serviço privilegiado
- WordPress for Android (GPLv2 ou posterior) — ideia de cliente de posts
- App GitHub (proprietária; só havia código jadx ofuscado) — ideia de navegação de repositórios

Dependências públicas declaradas: kotlinx.serialization (Apache 2.0), AndroidX (Apache 2.0), Jetpack Compose (Apache 2.0), kotlinx.coroutines (Apache 2.0), androidx.security:security-crypto (Apache 2.0), KTreeSitter (MIT) e grammars Tree-sitter Kotlin/Java/Python (consultar as licenças upstream específicas); testes: JUnit (EPL 1.0), org.json (JSON License).

## Espelhar-X — integração 1.10.0

O conceito, a especificação funcional e o código de referência Espelhar-X v1.0 foram fornecidos pelo utilizador sob licença MIT (Copyright (c) 2025 Espelhar-X). A integração HubOps 1.10.0 é uma implementação nativa independente em Kotlin/Jetpack Compose, baseada nas funcionalidades descritas; não incorpora literalmente os ficheiros Python/Vue/Capacitor de referência.

## Omega 1.28.0 — dependências adicionais e runtimes opcionais

- Apache Commons Compress 1.27.1 — Apache License 2.0 (extração TAR limitada).
- Bouncy Castle `bcprov-jdk18on` 1.78.1 — licença Bouncy Castle (MIT-style); consultar o ficheiro de licença distribuído pelo artefacto.
- llama.cpp — MIT; só é obtido pelo pipeline de build, não está incluído neste ZIP-fonte.
- PRoot — GPL-2.0-or-later; só é obtido pelo pipeline de build. A distribuição de binários deve incluir a licença e cumprir as obrigações de disponibilização do código-fonte correspondente.
- talloc — LGPL; só é obtido pelo pipeline de build. Confirmar a versão exata da licença e incluir o respetivo aviso no pacote final.
- TinyLlama/TinyLlama-1.1B-Chat-v1.0 — confirmar a licença e os termos do modelo na origem antes de redistribuir pesos; este ZIP não contém pesos.
- Dataset de GHSA/commits: cada fragmento de código conserva origem, repositório e commit; verificar licenças dos repositórios e termos aplicáveis antes de redistribuição ou treino comercial.
