<!-- LOVABLE:BEGIN -->
> [!IMPORTANT]
> This project is connected to [Lovable](https://lovable.dev). Avoid rewriting
> published git history — force pushing, or rebasing/amending/squashing commits
> that are already pushed — as it rewrites history on Lovable's side and the
> user will likely lose their project history.
>
> Commits you push to the connected branch sync back to Lovable and show up in
> the editor, so keep the branch in a working state.

## What Lovable manages

Rules outside this block are the project's own. Change the pieces below in
Lovable, not by hand. With the [Lovable MCP](https://mcp.lovable.dev)
connected, `send_message` asks Lovable to do it; otherwise ask the user to
use the editor.

- **Database.** Do not add or edit `supabase/migrations/` or `drizzle/`.
  Ask Lovable to change the schema.
- **Generated files.** Do not edit a file whose header says it is automatically
  generated.
- **AI gateway.** Keep model calls on Lovable's gateway, and keep
  `LOVABLE_API_KEY` out of browser code.
- **Connectors.** Lovable provides native connectors. `list_connectors` shows
  which are enabled; `add_connector` only returns the dashboard link. When a
  connector is enabled, calls go through the connector gateway and need
  `LOVABLE_API_KEY`. When Lovable does not provide one, users can bring their
  own key and store it in Secrets.
- **Secrets.** Lovable manages them. Do not put secret values in the repo.
<!-- LOVABLE:END -->

# HubEnd — project rules

- HubOps is the functional source of truth: its Kotlin code lives unchanged in `android/hubops` (Android library module `:hubops`); fixes are minimal, commented with `HubEnd:` and listed in docs/AUDITORIA.md. Why: the brief forbids replacing or reducing HubOps engines.
- The APK is assembled by `android/app` (Capacitor 7 shell from HybridApp) and depends on `:hubops`; `dev.hubops.HubOpsApplication` is the Application class. Why: one app, engines available to both the native console and the bridge.
- The web UI talks to engines only through the `HubEndEngines` Capacitor plugin (`android/app/.../bridge`), whose JSON shape is fixed by `BridgePayloads.kt` and mirrored in `src/lib/bridge/hubendEngines.ts` (bump CONTRACT_VERSION on both sides). Why: typed, testable contract between layers.
- The bridge never exposes shell, Termux jobs or ledger writes to the WebView; those stay in the native HubOps console. Why: the WebView may load remote content (APP_URL).
- In the browser the bridge returns `{ available: false }`; UI must never simulate engine results. Why: no fake capabilities.
- Engine capability statuses in `src/lib/engines/catalog.ts` must cite evidence (file or test) and use the six documented states; `src/lib/audit/matrix.ts` mirrors docs/AUDITORIA.md. Why: every announced capability must match a real implementation.
- Android build: one root Gradle (AGP 8.10.1, Kotlin 2.2.21, Gradle wrapper 8.11.1, Java 21, compileSdk 36, minSdk 26). `bunx cap sync android` must run before Gradle (it generates `capacitor-cordova-android-plugins`). Why: Capacitor 7 + Tree-sitter AAR constraints.
- Brand assets are real files in `public/brand`, `public/icons` and Android mipmaps (not CDN pointers) and the logo is always shown on its own light seal. Why: the GitHub repo and APK must build standalone and preserve the logo identity.
- Shared web services (eventBus, apiClient, store) come from HybridApp in `src/lib/core`. Why: reuse instead of duplication.
- HubOps tests also run on ART (`:hubops:connectedDebugAndroidTest -PhubendDeviceTests`, which raises minSdk only for that run) and `:app` has launch/console androidTests; CI runs both on an API 30 emulator. Why: JVM tests missed Android-only crashes (ICU regex, format strings) that killed APK 0.1.0 on launch.
- Debug APKs are signed with the committed `android/app/hubend-debug.keystore`. Why: each test build must install over the previous one; release signing stays secret.
