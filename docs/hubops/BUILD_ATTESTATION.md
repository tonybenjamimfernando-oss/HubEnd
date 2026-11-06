# Build Android e atestação de proveniência

## Pipeline incluído

`.github/workflows/android-ci.yml` executa em GitHub Actions:

1. JDK Temurin 17;
2. Gradle 8.11.1;
3. Android SDK Platform 34 e Build Tools 34.0.0;
4. `:app:testDebugUnitTest`;
5. `:app:lintDebug`;
6. `:app:assembleDebug`;
7. SHA-256 e metadados do commit/execução;
8. atestação GitHub de proveniência para o APK em eventos `push` e `workflow_dispatch` (não em pull requests).

O artefacto produzido é um **APK debug**, não uma versão assinada para distribuição. Não existe keystore de produção no repositório e nenhum segredo de assinatura foi criado ou presumido.

## Verificação independente

Depois de descarregar `app-debug.apk` e a respetiva atestação do mesmo run, use GitHub CLI autenticado e verifique contra o repositório de origem:

```bash
gh attestation verify app-debug.apk --repo OWNER/REPOSITORY
```

Substitua `OWNER/REPOSITORY` pelo repositório real. A verificação deve confirmar o sujeito (digest do APK), repositório, workflow e commit esperados. Não aceite apenas o ficheiro `build-metadata.txt` ou `SHA256SUMS`: são úteis para integridade, mas não substituem a atestação assinada.

## Limite de integração atual

O HubOps gera a avaliação cruzada, mas ainda não importa nem valida dentro da aplicação a bundle Sigstore/in-toto. Por isso, `CROSS-ENGINE-PROVENANCE` permanece `WARN` e o caso não pode ser marcado como totalmente concluído apenas com uma referência de build digitada. A atestação deve ser verificada externamente até existir um verificador integrado e uma política explícita de confiança no emissor.

## Execução local

Com JDK 17, Gradle 8.11.1 e Android SDK 34 instalados:

```bash
gradle --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

O ambiente de desenvolvimento desta alteração não tinha conectividade DNS para transferir Gradle/dependências nem Android SDK. O workflow foi criado, mas só ficará validado quando for executado num runner GitHub com acesso às dependências.
