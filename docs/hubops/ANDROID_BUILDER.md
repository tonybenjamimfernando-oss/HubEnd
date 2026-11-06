# Despertar Android Builder — guia operacional

## O que o botão faz

O botão **Compilar projeto e gerar APK no Termux** trabalha apenas com o último projeto criado pelo Project Forge:

1. Exporta o ZIP da pasta privada da app para `Download/HubOps/Projects` usando MediaStore.
2. Calcula SHA-256 durante a cópia e limita o ZIP a 50 MiB; o hash esperado é passado ao Termux e validado novamente antes de extrair.
3. Envia um comando `RUN_COMMAND` ao Termux. A app não executa comandos recebidos do conteúdo do ZIP.
4. O script verifica as ferramentas, as versões do JDK/Gradle e os componentes mínimos do Android SDK.
5. Valida o arquivo ZIP antes da extração: máximo de 500 entradas, 150 MiB descomprimidos, uma única pasta raiz, sem caminhos absolutos, `..` ou symlinks.
6. Executa `:app:assembleDebug` com dois workers e heap Java de 1,5 GiB.
7. Confirma que `app-debug.apk` existe e não está vazio, copia-o sem sobrescrever um APK anterior e imprime o SHA-256.

## Pré-requisitos no dispositivo

- Android 10 ou superior para exportação via MediaStore.
- Termux instalado e atualizado.
- `allow-external-apps=true` em `~/.termux/termux.properties`, Termux reiniciado e permissão Android RUN_COMMAND concedida à HubOps.
- Acesso ao armazenamento partilhado: executar `termux-setup-storage` e aceitar a permissão.
- JDK 17, Gradle 8.11.1–8.14.3, `unzip`/`zipinfo`.
- Android SDK Platform 34 e Build Tools 34.0.0. `ANDROID_HOME` ou `ANDROID_SDK_ROOT` deve apontar para a pasta SDK.
- Rede disponível na primeira compilação para transferir plugins e dependências Maven/Google.

O SDK Android em Termux pode exigir configuração adicional dependendo da origem dos pacotes e do dispositivo. O HubOps não instala nem distribui JDK, Gradle ou Android SDK.

## Localização dos artefactos

- Projeto ZIP: `Download/HubOps/Projects/<id>.zip`
- Projeto extraído para compilação: `~/despertar/builds/<id>/`
- APK debug: `Download/HubOps/Builds/<id>-debug.apk`

O projeto extraído é mantido para permitir inspeção dos logs e ficheiros `build/`. O APK é uma build debug, não uma release assinada para publicação.

## Diagnóstico de falhas

| Código | Significado / ação |
|---|---|
| 30 | Instalar `unzip` (que fornece `zipinfo`). |
| 31 | Gradle ausente; instalar Gradle 8.11.1–8.14.3. |
| 32 | Java ausente; instalar JDK 17. |
| 33 | ZIP inacessível; executar `termux-setup-storage` e confirmar permissões. |
| 34 | Pasta de trabalho já existe; não é apagada automaticamente. Escolher novo projeto ou inspecionar/remover manualmente a pasta antiga. |
| 35 | APK de destino já existe; não será sobrescrito. |
| 36 | Versão Gradle incompatível. |
| 37 | Java diferente de 17. |
| 38, 44 | Arquivo ZIP inseguro ou com symlink; gerar novamente pelo Project Forge. |
| 39, 40 | Estrutura ZIP/Gradle inesperada. |
| 41 | Gradle terminou sem o APK esperado. Consultar stdout/stderr. |
| 42 | Android SDK Platform 34 ou Build Tools 34.0.0 ausentes ou `ANDROID_HOME` não configurado. |
| 43 | Limites de arquivo ZIP excedidos. |
| 45 | O SHA-256 do ZIP no armazenamento partilhado difere do hash exportado; o ficheiro foi alterado ou corrompido e não é extraído. |

## Desempenho e limites

- Compilação serializada pela operação global do ViewModel; não são iniciados dois jobs Termux simultâneos pela UI.
- `--no-daemon` evita deixar um daemon Gradle a consumir RAM depois do build.
- `--max-workers=2` e heap de 1,5 GiB limitam a concorrência e a memória do processo Gradle.
- O processo ainda pode consumir centenas de MB adicionais com Android SDK, Kotlin compiler e dependências; fechar apps pesadas e manter espaço livre é recomendado.
- A primeira compilação é mais lenta por causa dos downloads e caches.
- A app guarda stdout/stderr até 12.000 caracteres por fluxo. O projeto extraído permanece disponível no Termux para diagnóstico detalhado.

## Segurança e estado de validação

- O ZIP é produzido pelo gerador local; o script não executa scripts de instalação nem tarefas arbitrárias do ZIP antes de validar a estrutura.
- O script só compila o template Android esperado e não instala automaticamente o APK no dispositivo.
- O APK final é `debug`; não usar para distribuição pública sem configurar assinatura de release, testes e revisão de segurança.
- Nesta entrega, o script foi testado com um Gradle/Java simulados e ferramentas ZIP reais para validar a orquestração e as verificações. A compilação Android real num dispositivo Termux ainda precisa de confirmação no ambiente do utilizador.

## Validação pós-compilação (Builder 1.3)

O Builder não publica um artefacto apenas porque o Gradle terminou com código zero. Antes da cópia para `Downloads/HubOps/Builds`, valida:

1. Integridade do contentor APK com `unzip -tq`.
2. Presença do `AndroidManifest.xml` e de pelo menos um ficheiro DEX.
3. `applicationId` do `app/build.gradle.kts` igual ao package extraído por `aapt dump badging`.
4. Assinatura criptográfica com `apksigner verify --verbose --print-certs`.
5. SHA-256 e tamanho do APK final.

O relatório `<id>-build-report.txt` é guardado ao lado do APK. O APK e o relatório não são sobrescritos. A interface só marca o artefacto como produzido quando recebe o callback de sucesso e encontra o marcador `BUILD_OK` acompanhado de um caminho permitido.

Códigos adicionais: 46 = package divergente; 47 = estrutura/integridade APK inválida; 48 = assinatura APK inválida. Estes controlos verificam integridade e assinatura, mas não substituem revisão de segurança, testes funcionais nem assinatura de release.
