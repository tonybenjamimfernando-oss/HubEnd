# AR-11 — Assinatura de release

Estado real: a configuracao de assinatura esta implementada e verificada pelo CI; o AAB/APK
assinado so existe depois de a chave ser adicionada aos segredos do repositorio. Ate la, o APK
publicado continua a ser o de debug.

## Segredos necessarios (Settings > Secrets and variables > Actions)

| Segredo | Conteudo |
|---|---|
| `HUBEND_KEYSTORE_BASE64` | keystore de release codificado em base64 (`base64 -w0 hubend-release.keystore`) |
| `HUBEND_KEYSTORE_PASSWORD` | palavra-passe do keystore |
| `HUBEND_KEY_ALIAS` | alias da chave de assinatura |
| `HUBEND_KEY_PASSWORD` | palavra-passe da chave |

Enquanto `HUBEND_KEYSTORE_BASE64` nao existir, os passos de release sao ignorados e o job
termina com sucesso: o repositorio nunca fica vermelho por falta de chave, e nunca e publicado
um artefacto de release sem assinatura.

## O que o CI faz quando a chave existe

1. Descodifica o keystore para `build/signing/` (fora do controlo de versao).
2. Executa `:app:bundleRelease` e `:app:assembleRelease` com as variaveis de ambiente.
3. Verifica o certificado com `apksigner verify --print-certs`.
4. Publica AAB, APK, SHA-256 e certificado como artefactos do workflow.
5. Remove a chave do runner (passo `if: always()`).

## Versao

`versionCode 5` / `versionName 0.1.4`. O incremento do `versionCode` e obrigatorio para que a
instalacao por cima da versao anterior seja aceite pelo Android — condicao do teste de
atualizacao exigido por AR-11.

## Limites (nao declarar como concluido)

- Instalacao limpa, atualizacao e rollback em dispositivo real continuam a exigir execucao manual
  com o APK assinado.
- O certificado de debug do APK publicado continua a ser de debug ate a chave ser configurada.
- SBOM e proveniencia do AAB assinado so ficam completos depois de existir um AAB real.