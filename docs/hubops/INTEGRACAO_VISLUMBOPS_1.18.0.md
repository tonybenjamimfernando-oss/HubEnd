# Integração HubOps + VislumbOps 1.18.0

## Decisão arquitetural

HubOps é a aplicação anfitriã e mantém o ciclo de vida Android, a Activity launcher e o router Compose. VislumbOps é incorporado como motor especializado, sem manter uma segunda aplicação ou uma segunda Activity launcher.

## Motores e responsabilidades

- **Despertar:** criação, geração, reparação e preparação de projetos.
- **Espelhar-X:** inspeção, análise estrutural/AST, comparação e gestão de workspace.
- **VislumbOps:** inspeção de APK, integridade, inventário de cadeia de fornecimento, permissões, segredos, dependências, análise estática, evidências, certificação local e relatórios.
- **HubOps Core:** configuração, eventos, orquestração, proveniência, registo e integração entre motores.

## Registry e política de intercâmbio

`HubOpsEngineRegistry` regista os quatro motores e define fluxos permitidos. Despertar → Espelhar-X é mutação governada; Espelhar-X → Despertar é análise somente-leitura; VislumbOps → HubOps é troca de evidências. Não existe rota direta VislumbOps → workspace de Despertar/Espelhar-X.

## Corrente de verificação

1. O utilizador seleciona um APK através do seletor de documentos Android.
2. VislumbOps copia o artefacto para cache privada com limite de 100 MB e executa as suas análises.
3. O orquestrador gera contrato, verificações, achados, cadeia de evidências, score, eventual atestação local e relatório.
4. `HubOpsVerificationRelay` passa o contrato pelo `HubOpsBridge`.
5. A aprovação só é reconhecida se a versão do contrato, o SHA-256, a cadeia de evidências e a atestação local forem válidos.
6. O resultado é gravado no ledger comum com hashes do APK e do relatório, origem/destino, contagens e `mutations=0`.

O relay não importa nem altera código do projeto. Um resultado rejeitado ou que exija remediação é registado com o respetivo estado; não é promovido a aprovação.

## Segurança e limites

- Não foi adicionada uma segunda Activity launcher nem um segundo objeto Application.
- O serviço foreground do app Vislumbrops independente não foi incorporado: a interface integrada executa a verificação no ViewModel e permite cancelamento. Isso evita permissões e notificações duplicadas.
- A execução dinâmica continua desativada por padrão; `SandboxRunner` reporta laboratório indisponível em vez de instalar/executar o APK no dispositivo do utilizador.
- O certificado é uma atestação HMAC local ao Android Keystore, não uma assinatura pública nem uma certificação de terceiros.
- A integração é somente-leitura; as transferências mutáveis entre Despertar e Espelhar-X continuam a passar pelo fluxo bifásico existente.

## Verificação pendente

Não há Gradle/Android SDK neste ambiente. Portanto, ainda não foram executados `testDebugUnitTest`, `assembleDebug`, `assembleRelease`, nem testes instrumentados. O código foi integrado e inspecionado estruturalmente, mas a compatibilidade final de Compose/Gradle e Android precisa de validação em Android Studio ou CI.
