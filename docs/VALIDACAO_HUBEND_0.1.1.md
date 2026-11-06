# HubEnd 0.1.1 — Correção do fecho ao abrir

Data: 6 nov 2026 · Ambiente: Linux x86_64, JDK 21 (Temurin), Gradle 8.11.1, Android SDK 36,
**emulador Android 11 (API 30, x86_64, sem aceleração de hardware)**.

## Causa do fecho do APK 0.1.0 (reproduzida no emulador)

```
FATAL EXCEPTION: main — Unable to create application dev.hubops.HubOpsApplication:
java.util.regex.PatternSyntaxException: Syntax error in regexp pattern near index 39
checkServerTrusted\s*\([^)]*\)\s*\{\s*}
  at dev.hubops.omega.ai.SymbolicPatchArbiter.<init>(SnseEngine.kt:144)
```

O padrão é válido na JVM (onde os testes passavam) mas o motor de regex ICU do Android rejeita `}` sem escape.
Um segundo fecho, ao abrir a consola nativa, vinha do `%` da bateria dentro de um padrão `format()`
(`FeedScreen.kt:37`). Ambos corrigidos; ver AUDITORIA.md §3, linhas 6–9.

## Executado e aprovado

| Verificação | Comando | Resultado |
|---|---|---|
| Testes HubOps na JVM | `./gradlew :hubops:testDebugUnitTest` | **171 testes, 0 falhas** |
| Testes HubOps em Android (ART) | `./gradlew :hubops:connectedDebugAndroidTest -PhubendDeviceTests` | **171/171, 0 falhas** no emulador API 30 |
| Testes da ponte | `./gradlew :app:testDebugUnitTest` | **5 testes, 0 falhas** |
| Arranque e consola em Android | `./gradlew :app:connectedDebugAndroidTest` | **3/3**: Application inicializa os motores; ecrã HubEnd chega a RESUMED; os 10 ecrãs da consola abrem sem fechar |
| Abertura manual | `adb install` + `am start` | Ecrã local mostra o estado real dos 4 motores e da suite Omega; botão abre a consola (Feed com bateria, RAM, armazenamento) |
| APK debug | `./gradlew :app:assembleDebug` | `dev.hubend.app` 0.1.1 (versionCode 2), minSdk 26, targetSdk 35 |

## Notas

- O APK 0.1.1 é assinado com uma chave de depuração nova e fixa: **desinstale o 0.1.0 antes de instalar**.
  As versões de teste seguintes instalam por cima.
- O emulador corre sem aceleração, por isso não mede a velocidade real; os tempos num telemóvel são muito menores.
- Por validar: telemóvel físico do utilizador; APK release (chave de assinatura); Fases 1–4.
