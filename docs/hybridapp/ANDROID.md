# HybridApp no Android (APK)

A app web é embrulhada pelo **Capacitor** numa app Android nativa. No telemóvel ganha:

| Funcionalidade | No APK | No navegador |
| --- | --- | --- |
| Base de dados | SQLite (`hybridapp.db`) + armazenamento local | Armazenamento local |
| Biometria | Impressão digital / rosto (NativeBiometric) | Chave de acesso do dispositivo (WebAuthn) |
| Notificações | Push (Firebase) + notificações locais | Notificações do navegador |
| Segundo plano | Retoma tarefas ao voltar à app, botão "voltar" | Retoma ao voltar ao separador |

## 1. Publicar a app
Publica a app no Lovable e copia o endereço publicado (ex.: `https://a-tua-app.lovable.app`).

## 2. Compilar o APK sem computador (GitHub Actions)
1. No repositório GitHub: **Settings → Secrets and variables → Actions → Variables** → cria `APP_URL` com o endereço publicado.
2. (Opcional, para push) em **Secrets** cria `GOOGLE_SERVICES_JSON` com o conteúdo do ficheiro do Firebase.
3. Separador **Actions → Compilar APK Android → Run workflow**.
4. Quando terminar, descarrega o artefacto **HybridApp-apk** e instala-o no telemóvel.

## 3. Compilar num computador
```bash
bun install
APP_URL=https://a-tua-app.lovable.app bunx cap add android
node scripts/android-setup.mjs
APP_URL=https://a-tua-app.lovable.app bunx cap sync android
cd android && ./gradlew assembleDebug
```
O APK fica em `android/app/build/outputs/apk/debug/`.

## Notificações push (Firebase)
1. Cria um projeto em console.firebase.google.com e adiciona uma app Android com o ID `com.tonybenjamim.hybridapp`.
2. Descarrega `google-services.json` e usa-o como segredo (passo 2) ou coloca-o na raiz do projeto.
Sem este ficheiro tudo funciona, exceto o push remoto.
