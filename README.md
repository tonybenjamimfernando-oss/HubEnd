<p align="center"><img src="docs/brand/hubend-logo.png" alt="HubEnd" width="180" /></p>

# HubEnd

Plataforma Android de desenvolvimento e operação que integra o **HubOps 1.29.0** (motores autónomos em Kotlin,
fonte de verdade funcional) com a infraestrutura híbrida do **HybridApp** (React + TanStack Start + Capacitor 7).

- **Motores**: HubOps Core · Espelhar-X · Despertar · VislumbOps · SNSE · NSC · GoPA
- **Interface**: Hub Central, Motores (uma área por motor), Compilação, Integração GitHub, Sistema, Auditoria
- **Ponte**: plugin Capacitor `HubEndEngines` com contrato JSON testado
- **Consola nativa**: os 9 ecrãs Compose do HubOps continuam completos dentro do APK

Auditoria, matriz de integração e problemas corrigidos: [`docs/AUDITORIA.md`](docs/AUDITORIA.md).
Evidências de validação: [`docs/VALIDACAO_HUBEND_0.1.0.md`](docs/VALIDACAO_HUBEND_0.1.0.md).

## Estrutura

```text
android/            Gradle raiz (wrapper 8.11.1, AGP 8.10.1, Kotlin 2.2.21, Java 21)
  app/              Shell Capacitor + ponte HubEndEngines (Kotlin) + testes do contrato
  hubops/           HubOps 1.29.0 como biblioteca: motores, consola Compose, 171 testes
android-shell/      Ecrã local do APK quando não há APP_URL
src/                Interface HubEnd (rotas, componentes, serviços partilhados, ponte TS)
tools/              GoPA (Go), SNSE, NSC, qualidade estática
docs/               Auditoria, validação, documentação HubOps/HybridApp, marca
.github/workflows/  CI Android: testes, lint, APK debug e proveniência
```

## Compilar

Requisitos: Bun, JDK 21, Android SDK (platform 36, build-tools 36.0.0).

```bash
bun install
bunx vitest run                      # testes da interface
bunx cap sync android                # prepara o shell Capacitor
cd android
./gradlew :hubops:testDebugUnitTest :app:testDebugUnitTest   # 171 + 5 testes
./gradlew :app:assembleDebug         # android/app/build/outputs/apk/debug/app-debug.apk
```

Interface completa no APK: publique a app web e compile com `APP_URL=https://… bunx cap sync android`
(no GitHub: **Settings → Secrets and variables → Actions → Variables → `APP_URL`**).
Sem `APP_URL` o APK abre o ecrã local, que lê o estado real dos motores e abre a consola nativa HubOps.

## Estados de funcionalidade

Cada capacidade tem um estado documentado com evidência no código:
**Implementada e testada · Implementada · Parcial · Preparada, não operacional · Pendente · Bloqueada por dependência externa.**
A interface mostra-os por motor; a fonte é `src/lib/engines/catalog.ts`.

## Licenças

Código HubOps: MIT (`LICENSE`). Atribuições e dependências: [`docs/hubops/NOTICE.md`](docs/hubops/NOTICE.md).
PRoot (GPL-2.0+), llama.cpp (MIT) e modelos não são incluídos no APK.
