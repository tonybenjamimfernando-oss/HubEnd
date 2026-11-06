# Validação 1.18.0 — HubOps + VislumbOps

## Confirmado nesta entrega
- Arquivos-fonte do motor VislumbOps 1.1.0 copiados para o módulo `:app` mantendo os namespaces `com.hubops.vislumbrops` e `dev.hubops`.
- Interface de verificação adicionada à navegação principal do HubOps.
- Uma única `HubOpsApplication` inicializa o `ServiceLocator` VislumbOps.
- Uma única Activity launcher permanece declarada no Manifest.
- `HubOpsEngineRegistry` declara quatro motores e bloqueia fluxos não registados.
- `HubOpsVerificationRelay` encaminha contratos para classificação pelo `HubOpsBridge` e regista resultados no ledger HubOps.
- O registo externo exige hashes SHA-256 válidos, IDs não vazios, estados enumerados e contagens não negativas.
- Testes JUnit adicionados para os invariantes do registo externo.
- `kotlinc` compilou isoladamente o `HubOpsOrchestrator`, o `HubOpsEngineRegistry` e o `HubOpsVerificationRelay` com stubs temporários para os tipos Android/VislumbOps; smoke test executou publicação no ledger e confirmou `mutations=0`.
- As fontes dos novos testes foram verificadas sintaticamente com stubs JUnit; não foram executadas pelo runner JUnit.
- Manifest, recursos JSON, testes e documentação do motor foram incorporados.

## Não executado / não certificado
- `./gradlew testDebugUnitTest` e `./gradlew assembleDebug` — wrapper executável não incluído no pacote de origem e Gradle/Android SDK indisponíveis neste ambiente.
- Compilação Kotlin/Compose integrada de todo o módulo Android.
- Execução dos testes JUnit/Android reais.
- Verificação do comportamento em dispositivo físico, incluindo Android Keystore e SAF.
- Testes de carga e de APKs hostis num laboratório isolado.

## Riscos remanescentes
- O arquivo Vislumbrops fornecido não continha `LICENSE`; confirmar direitos/licença antes de distribuição pública.
- As versões de dependências foram alinhadas ao build anfitrião, mas ainda precisam de resolução Gradle real.
- O ledger partilhado regista o resultado de auditoria, mas não torna a aprovação equivalente a aceitação de uma transferência de código.
