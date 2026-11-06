<!-- LOVABLE:BEGIN -->
> [!IMPORTANT]
> This project is connected to [Lovable](https://lovable.dev). Avoid rewriting
> published git history — force pushing, or rebasing/amending/squashing commits
> that are already pushed — as it rewrites history on Lovable's side and the
> user will likely lose their project history.
>
> Commits you push to the connected branch sync back to Lovable and show up in
> the editor, so keep the branch in a working state.
<!-- LOVABLE:END -->

# Project rules

- Web port of the user's Vue/Capacitor "HybridApp MVP" (GitHub client); keep feature parity with the original modules (core, github, terminal, notifications). Why: the user asked to evolve their existing app, not start a new one.
- Core services live in `src/lib/core` (eventBus, apiClient, store); modules talk through the eventBus. Why: mirrors the original modular architecture.
- Device data stays on the device: tables (users, notifications, tasks) go through `src/lib/core/database.ts` (atomic `transaction()`), backed by localStorage via `store.ts` and mirrored to SQLite in the APK (`src/lib/native/sqlite.ts`). Why: the original used on-device SQLite and data is per-device.
- Native features live in `src/lib/native/*`, detect Capacitor at runtime and dynamically import plugins, each with a browser fallback (WebAuthn, Notification API, visibilitychange). Why: one codebase runs in the browser and in the APK.
- The APK (capacitor.config.ts) loads the published URL via `server.url` (APP_URL) because login needs server functions; push registers only when the build had Firebase (user-agent flag), else the app would crash. Why: TanStack Start is not a static bundle.
- Pages that need a GitHub session live under `src/routes/_authenticated/` (client-only guard using the stored token). Why: the token only exists on the device.
- GitHub Device Flow token exchange runs in `src/lib/github/oauth.functions.ts` server functions (no client secret needed). Why: github.com OAuth endpoints block browser CORS.
- GitHub data is fetched client-side with the user's token via TanStack Query (not route loaders). Why: the token only exists in the browser.
- ApiClient retries only network errors and 5xx; terminal tasks retry at most MAX_ATTEMPTS and never retry usage/unknown-command errors. Why: the original looped forever on failures.
- GitHub Sentinel: CVE list in `src/lib/core/vulnerabilityDb.ts` (offline, real advisory IDs only), scan/patch logic in `src/lib/security/*`, results in the `vulnerabilities` and `repoScans` tables of `database.ts`. Patches open one PR per package.json and delete the branch if a later step fails. Why: offline-first audit with no orphan branches.
