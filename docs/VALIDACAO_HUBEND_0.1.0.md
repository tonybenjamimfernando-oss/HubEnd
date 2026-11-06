# HubEnd 0.1.0 — Validação da Fase 0

Data: 6 nov 2026 · Ambiente: Linux x86_64, JDK 21 (Temurin), Gradle 8.11.1, Android SDK platform 36 / build-tools 36.0.0.

## Executado e aprovado

| Verificação | Comando | Resultado |
|---|---|---|
| Testes HubOps | `./gradlew :hubops:testDebugUnitTest` | **171 testes, 0 falhas** (primeira execução real; 4 defeitos corrigidos, ver AUDITORIA.md §3) |
| Testes da ponte | `./gradlew :app:testDebugUnitTest` | **5 testes, 0 falhas** (`BridgePayloadsTest`) |
| Lint Android | `./gradlew :hubops:lintDebug :app:lintDebug` | **0 erros** (30 + 32 avisos; 2 erros NewApi corrigidos) |
| APK debug | `./gradlew :app:assembleDebug` | **Compilado** — `app-debug.apk`, 33 MB |
| Testes da interface | `bunx vitest run` | **15 testes, 0 falhas** |
| Qualidade estática | `tools/quality/check_kotlin_static.py`, `validate_cve_db.py` | Aprovado (111 ficheiros Kotlin; 11 CVE, 15 vetores) |
| Interface web | Navegador 390×844 em todas as rotas | Sem erros de execução |
| CI GitHub Actions | `android-ci.yml`, execução 42266041177 (commit d35df12) | **Aprovado**: GoPA `go test`, SNSE/NSC, Vitest, testes Android, lint, APK debug, atestação de proveniência (APK sha256 `83ec9501…f6beea7`) |

Manifesto do APK (`aapt dump badging`): pacote `dev.hubend.app`, versão 0.1.0, minSdk 26, targetSdk 35,
compileSdk 36, atividade de arranque `dev.hubend.app.MainActivity`, Application `dev.hubops.HubOpsApplication`,
consola nativa `dev.hubops.ui.MainActivity` (não exportada), permissões `INTERNET` e `com.termux.permission.RUN_COMMAND`.
Bibliotecas nativas Tree-sitter para arm64-v8a, armeabi-v7a, x86_64 e x86.

## Não executado / por validar

| Item | Motivo | Próximo passo |
|---|---|---|
| Instalação e navegação num dispositivo | Sem dispositivo nem emulador (sem KVM) neste ambiente | Instalar o APK do CI e abrir a app; confirmar ecrã local, estado dos motores e consola nativa |
| Ponte `HubEndEngines` em execução | Depende do dispositivo | Idem |
| APK release | Sem chave de assinatura | Configurar keystore em segredos do repositório |
| llama.cpp, PRoot, modelos SNSE | Fases 1–3 | — |

Nota: a primeira execução do CI falhou porque a action `setup-android` tentava instalar o pacote legado `tools`, que já não existe; corrigido em d35df12.
