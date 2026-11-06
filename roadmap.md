# Roadmap — HubEnd

- [x] Entrega A — Auditoria (inventário, matriz de integração, problemas, arquitetura) em docs/AUDITORIA.md
- [x] Fase 0 — Projeto Android unificado: Capacitor (shell HybridApp) + HubOps como módulo :hubops
- [x] Fase 0 — Ponte nativa HubEndEngines (contrato JSON testado + fallback honesto no navegador)
- [x] Fase 0 — Interface HubEnd: logótipo, Hub Central, Motores (7 áreas), Compilação, Integração, Sistema, Auditoria
- [x] Fase 0 — CI GitHub Actions: testes, lint, APK debug, proveniência
- [x] Fase 0 — Compilado e testado localmente (171 + 5 testes, lint, assembleDebug)
- [x] Publicar no repositório GitHub HubEnd (CI verde, APK como artefacto)
- [x] Corrigir fecho ao abrir (APK 0.1.1) e testar em emulador Android: arranque, 171 testes HubOps, 10 ecrãs da consola
- [x] CI: testes em emulador Android a cada envio
- [x] APK 0.1.1 abre num telemóvel real (Xiaomi, Android 11) — confirmado pelo utilizador
- [x] Corrigir separadores invisíveis da consola nativa (0.1.2) e reforçar o teste
- [ ] Confirmar navegação da 0.1.2 no telemóvel real — requer o utilizador
- [x] Explicar ao utilizador como começar (GitHub, Terminal, WordPress)
- [ ] Interface completa dentro do APK — requer publicar a app e definir APP_URL
- [ ] Fase 1 — SQLite, biometria, notificações (shell); llama.cpp/JNI arm64-v8a
- [ ] Fase 2 — SNSE neural (após Fase 1)
- [ ] Fase 3 — Termux RUN_COMMAND, PRoot
- [ ] Fase 4 — Google Play Billing com validação no servidor
