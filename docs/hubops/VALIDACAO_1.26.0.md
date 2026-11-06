# Validação 1.26.0

## Alterações verificadas localmente

- Corrigidos os imports de `CrossEngineAssessment.kt` e do respetivo teste: o contrato real está em `com.hubops.vislumbrops.core`, não em `dev.hubops.vislumbrops.core`.
- Compilação Kotlin isolada de `ProfessionalWorkflowCoordinator.kt`: aprovada.
- Harness Kotlin de persistência: aprovado.
- Oito métodos de teste do coordenador executados em harness JVM com shim JUnit: 8/8 aprovados.
- Quatro métodos de teste da avaliação cruzada executados em harness JVM com shim JUnit: 4/4 aprovados.
- Simulação de interrupção antes do append do diário: recuperação concluiu o evento e o snapshot.
- Simulação de interrupção depois do append e antes do snapshot: recuperação não duplicou o evento.
- Reabertura repetida do coordenador: hash da cabeça do diário permaneceu estável.
- Compilação isolada do analisador cruzado e dependências puras: aprovada.
- Harness de avaliação: cobertura AST incompleta e proveniência não verificada produziram `WARN` e exigiram revisão.
- Workflow YAML analisado e bloco shell validado com `bash -n`.
- Workflow GitHub Actions e documentação de atestação adicionados.

## Limitações objetivas

- Não foi possível executar Gradle, JUnit oficial, lint Android ou assembleDebug localmente: não há Gradle instalado, o pacote de entrada não contém `gradlew`/`gradle-wrapper.jar`, não há Android SDK e o ambiente não resolve DNS para descarregar ferramentas/dependências.
- O workflow remoto ainda não foi executado; a sua eficácia só fica confirmada após um run GitHub bem-sucedido.
- A atestação GitHub é produzida para o APK debug em push/manual dispatch, mas o HubOps ainda não importa nem valida a bundle Sigstore/in-toto internamente. A avaliação cruzada mantém proveniência em `WARN` e não encerra automaticamente o caso.
- Não existe assinatura de release nem keystore de produção. O artefacto CI é debug e não deve ser distribuído como versão de produção.
- Testes instrumentados, testes de UI em dispositivo e validação de APK real continuam pendentes.

## Comando de validação no runner Android

```bash
gradle --no-daemon --stacktrace clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

O workflow executa esse comando em GitHub Actions com JDK 17, Gradle 8.11.1 e Android SDK 34.
