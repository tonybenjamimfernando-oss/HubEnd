import type { CapacitorConfig } from "@capacitor/cli";

/**
 * HubEnd — configuração do APK Android (Capacitor 7, herdado do HybridApp).
 *
 * A interface HubEnd (TanStack Start) é servida a partir do endereço publicado
 * (APP_URL) dentro da WebView nativa, porque usa funções de servidor. Sem APP_URL
 * o APK abre o ecrã local em android-shell/, que continua a dar acesso direto
 * à consola nativa HubOps (motores Kotlin) através da ponte HubEndEngines.
 */
const appUrl = process.env["APP_URL"];

const config: CapacitorConfig = {
  appId: "dev.hubend.app",
  appName: "HubEnd",
  webDir: "android-shell",
  backgroundColor: "#0e1210",
  android: {
    allowMixedContent: false,
    captureInput: true,
    appendUserAgent: "HubEnd/1",
  },
  ...(appUrl
    ? {
        server: {
          url: appUrl,
          cleartext: false,
          androidScheme: "https",
          allowNavigation: [new URL(appUrl).host, "github.com", "*.github.com", "api.github.com"],
        },
      }
    : {}),
};

export default config;
