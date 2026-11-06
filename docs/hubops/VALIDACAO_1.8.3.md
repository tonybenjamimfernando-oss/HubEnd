# Validação técnica — HubOps Despertar 1.8.3

## Alterações desta versão

- A auditoria estática agora associa cada alerta ao caminho e número da linha.
- Os padrões de cada ficheiro são analisados isoladamente; a junção de conteúdos de ficheiros diferentes já não pode criar artificialmente um alerta de TLS.
- A verificação de `X509TrustManager`/`HostnameVerifier` continua a considerar contexto no mesmo ficheiro, com janela limitada.
- Os alertas continuam a ser heurísticos e exigem confirmação humana.

## Verificações executadas

- Compilação isolada de `StaticCodeAudit.kt` com `kotlinc`: concluída.
- Quatro verificações smoke: localização de linha, deteção de execução de processos, prevenção de falso positivo entre ficheiros e deteção contextual TLS: aprovadas.
- Os 13 testes do motor de similaridade da versão 1.8.2 mantêm-se aplicáveis porque o respetivo código não foi alterado nesta versão.

## Limitações não resolvidas

- Não foi executado `assembleDebug` nem `testDebugUnitTest`: o ambiente não dispõe de Gradle instalado, Android SDK configurado ou executável `gradlew`/`gradle-wrapper.jar` no projeto.
- Não há ainda análise AST real nem resolução semântica de símbolos para Kotlin, Java ou Python.
- Esta auditoria não é SAST completo, não interpreta integralmente comentários/literais e não certifica ausência de vulnerabilidades.
