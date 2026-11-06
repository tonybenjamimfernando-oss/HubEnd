# Roadmap — HybridApp (MVP) web

- [x] Port core: EventBus, ApiClient (fix 4xx retry bug), local store (replaces SQLite)
- [x] GitHub login: Device Flow via server function + Personal Access Token
- [x] Home: profile, stats, repo list with search/sort
- [x] Repo page (was empty): files, README, issues, PRs, commits, star, new issue
- [x] Explore: search public repos (works signed out)
- [x] Notifications: GitHub inbox + local activity, mark read
- [x] Terminal: task queue with bounded retries + new commands
- [x] Settings: theme, session, clear local data
- [x] Tests for core rules
- [x] App instalável (ícone, ecrã inteiro)
- [x] Base de dados local com transações + registo de utilizadores + SQLite no APK
- [x] Biometria (bloqueio da app), preparação push, rotas protegidas, perfil
- [x] Agendador em segundo plano + comandos db/device/jobs
- [x] Capacitor + script Android + workflow GitHub para compilar APK
- [x] GitHub Sentinel: análise de dependências, CVEs locais, PR de correção, painel
- [ ] Compilar o APK — depende de publicar a app e definir APP_URL no GitHub
