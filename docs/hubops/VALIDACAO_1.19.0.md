# Validação 1.19.0

## Alteração
Foi acrescentado `ApkSignatureVerificationEngine`, motor independente que consulta `PackageManager.getPackageArchiveInfo` com `GET_SIGNING_CERTIFICATES` (API 28+) ou `GET_SIGNATURES` (API 26–27). Os certificados expostos pela plataforma são convertidos em fingerprints SHA-256 e adicionados aos metadados do contrato de verificação.

## Política de decisão
- APK ilegível/vazio: `ERROR`.
- PackageManager não consegue interpretar o APK: `FAIL`.
- APK interpretado mas sem certificado exposto: `ERROR`.
- Certificados obtidos: `PASS` para a verificação de assinatura feita pelo parser Android e para o cálculo de fingerprints.
- Android anterior à API 28: histórico de rotação é `SKIPPED`, pois a API não o expõe.

## Limites importantes
Esta implementação depende da validação de assinatura realizada pelo parser Android ao interpretar o APK. Não reporta qual esquema v1/v2/v3/v4 foi usado e não substitui `apksig` para validação explícita de esquemas, rotação e lineage. Os fingerprints identificam certificados, não a identidade civil do publicador.

## Verificações executadas neste ambiente
- Inspeção estrutural das alterações e dos pontos de injeção no `ServiceLocator` e no `VerificationOrchestrator`.
- Verificação da configuração de versão: `versionCode=26`, `versionName=1.19.0`.
- Verificação de consistência do ZIP final e de ausência de corrupção.

## Não executado
- Gradle/JUnit, compilação Android, APK debug/release e validação em dispositivo: não há Gradle/Android SDK neste ambiente.
- Não foi feita validação empírica com APKs assinados, não assinados, corrompidos ou com rotação de certificados neste ambiente.
