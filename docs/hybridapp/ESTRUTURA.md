# MVP — HybridApp (versão web + APK Android)

Cliente GitHub com terminal de tarefas, notificações, base de dados local e
módulo de segurança (GitHub Sentinel). Uma só base de código corre no navegador
e dentro do APK Android (Capacitor).

## Como usar

```bash
bun install          # instala dependências
bun run dev          # abre a app em http://localhost:8080
bun run test         # corre os testes (vitest)
bun run lint         # verifica o código
bun run build        # compila para produção
```

Android (APK): ver `ANDROID.md` e `scripts/android-setup.mjs`.
O fluxo `Compilar APK Android` está em `.github/workflows/android-apk.yml`.

## Estrutura de ponta a ponta

```
.
├── package.json                 dependências e comandos (dev, build, test, lint)
├── bun.lock                     versões fixas das dependências
├── vite.config.ts               Vite + TanStack Start + Tailwind v4
├── vitest.config.ts             configuração dos testes
├── tsconfig.json / .prettierrc  TypeScript e formatação
├── eslint.config.js             regras de código
├── components.json              configuração dos componentes de interface
├── capacitor.config.ts          configuração do APK (url da app, plugins)
├── .gitignore / .prettierignore
├── README.md                    visão geral
├── ANDROID.md                   passos para gerar o APK
├── AGENTS.md                    regras técnicas do projeto
├── roadmap.md                   estado das funcionalidades
│
├── scripts/
│   └── android-setup.mjs        prepara o projeto Android: permissões, ícone, Firebase opcional
├── .github/workflows/
│   └── android-apk.yml          compila o APK debug automaticamente
├── android-shell/
│   ├── index.html               página de arranque do shell Android
│   └── icon.png                 ícone da app
│
├── public/                      ficheiros servidos tal como estão
│   ├── manifest.webmanifest     app instalável no telemóvel
│   ├── favicon.png, apple-touch-icon.png
│   ├── icons/icon-192.png, icon-512.png
│   └── robots.txt
│
└── src/
    ├── router.tsx               cria o router TanStack
    ├── routeTree.gen.ts         árvore de rotas gerada automaticamente
    ├── server.ts                entrada do servidor (Cloudflare/edge)
    ├── start.ts                 arranque do TanStack Start + proteção CSRF
    ├── styles.css               tema: fundo escuro, acento verde, tokens de cor
    │
    ├── routes/                  páginas (uma por endereço)
    │   ├── __root.tsx           layout comum a todas as páginas
    │   ├── index.tsx            página inicial
    │   ├── login.tsx            entrar com GitHub
    │   ├── explore.tsx          procurar repositórios públicos
    │   ├── u.$login.tsx         perfil público de um utilizador
    │   ├── repo.$owner.$name.tsx  repositório: ficheiros, README, issues, PRs, commits
    │   ├── terminal.tsx         terminal com fila de tarefas e histórico
    │   ├── settings.tsx         definições
    │   └── _authenticated/      páginas que exigem sessão (proteção no cliente)
    │       ├── route.tsx        barreira de autenticação
    │       ├── notifications.tsx  notificações do GitHub
    │       ├── profile.tsx      perfil e sessão
    │       └── security.tsx     painel GitHub Sentinel
    │
    ├── components/
    │   ├── app/                 peças da aplicação
    │   │   ├── AppShell.tsx     cabeçalho, menu e navegação
    │   │   ├── AppLock.tsx      bloqueio biométrico
    │   │   ├── DeviceSettings.tsx  definições do dispositivo e base de dados
    │   │   ├── LoginPanel.tsx   painel de entrada
    │   │   └── bits.tsx         blocos partilhados (títulos, avisos, vazio)
    │   ├── security/            peças do Sentinel
    │   │   ├── StatCard.tsx     cartão de estatística
    │   │   └── VulnCard.tsx     cartão de vulnerabilidade e sua correção
    │   └── ui/                  componentes de interface base (Radix + Tailwind)
    │
    ├── hooks/
    │   └── use-mobile.tsx       deteta ecrã pequeno
    │
    └── lib/
        ├── auth.tsx             sessão do utilizador
        ├── route-guard.ts       decisão de quem pode abrir cada página
        ├── theme.ts             tema claro/escuro
        ├── utils.ts             utilitários gerais
        ├── error-capture.ts     apanha erros do servidor
        ├── error-page.ts        página de erro
        ├── lovable-error-reporting.ts  envio de erros para diagnóstico
        │
        ├── core/                núcleo comum a todos os módulos
        │   ├── eventBus.ts      mensagens entre módulos
        │   ├── apiClient.ts     pedidos HTTP com sessão, tentativas e limites
        │   ├── store.ts         escrita/leitura local (localStorage)
        │   ├── database.ts      tabelas locais e transações atómicas
        │   ├── models/security.ts  modelos de análise e vulnerabilidade
        │   └── vulnerabilityDb.ts  base local de avisos (CVE) e comparação de versões
        │
        ├── github/
        │   ├── service.ts       API do GitHub: perfil, repositórios, ficheiros,
        │   │                    issues, PRs, commits, branches e correções
        │   └── oauth.functions.ts  entrada pelo código do dispositivo (server function)
        │
        ├── terminal/
        │   ├── service.ts       fila de tarefas, estados, resultados e histórico
        │   ├── github-commands.ts  comandos do GitHub
        │   └── device-commands.ts  comandos do dispositivo
        │
        ├── security/            GitHub Sentinel
        │   ├── scannerService.ts   analisar manifests, guardar resultados e estatísticas
        │   ├── patchService.ts     criar branch, alterar ficheiro e abrir pull request
        │   └── index.ts            exportações do módulo
        │
        ├── native/              funcionalidades do telemóvel (com alternativa no navegador)
        │   ├── index.ts         arranque dos serviços nativos
        │   ├── platform.ts      detetar Android/iOS e plugins disponíveis
        │   ├── sqlite.ts        cópia SQLite no Android (hybridapp.db)
        │   ├── biometric.ts     impressão digital / rosto (plugin ou WebAuthn)
        │   ├── push.ts          notificações push (Firebase) e locais
        │   └── background.ts    tarefas periódicas em segundo plano
        │
        ├── core.test.ts         testes do núcleo
        ├── device.test.ts       testes dos serviços do dispositivo
        └── security.test.ts     testes do Sentinel
```

## Estado da verificação

- Compilação e 25 testes a passar; páginas abertas no navegador com conta GitHub simulada.
- APK, biometria e SQLite no telemóvel ainda não foram testados num dispositivo real.
