# Arquitetura 1.1.0

## Motor novo: Artifact Supply-Chain Engine

`ArtifactSupplyChainEngine` é uma unidade autónoma com entrada `artifactPath` e saída `Audit(result, manifest)`. Não depende do `VerificationOrchestrator`, do HubOps nem da interface. Pode ser chamada isoladamente por testes ou por outro motor.

Responsabilidades:
1. Validar ficheiro e limites do arquivo.
2. Percorrer a tabela ZIP sem extrair conteúdo.
3. Bloquear caminhos absolutos, traversal e entradas duplicadas.
4. Ler conteúdo em streaming com limites por entrada e globais.
5. Calcular SHA-256 de cada entrada e do artefacto.
6. Inventariar DEX, bibliotecas nativas e metadados de assinatura v1.
7. Sinalizar compressão anormal e entradas demasiado grandes.
8. Gerar manifesto JSON e hash determinístico; a interface permite exportá-lo via SAF.

Não executa código, não instala APK, não altera o ficheiro de origem e não afirma validar a assinatura criptográfica.

## Fluxo coordenado

`SAF -> cache privada -> APK Inspector + scanners paralelos + Supply-Chain Engine -> score -> evidence chain -> atestação local -> relatório`

O novo motor não substitui os anteriores. O orquestrador agrega os seus resultados, enquanto o motor conserva API e modelos próprios.

## Segurança e confiança

- Nenhum conteúdo ZIP é extraído para o sistema de ficheiros.
- Limites padrão: APK 100 MB, 10.000 entradas, 64 MB por entrada e 300 MB expandidos.
- Chave HMAC de atestação fica no Android Keystore.
- Certificados são atestações locais; HubOps só aceita a verificação local da cadeia e da assinatura no mesmo contexto de chave.
- Runtime ADB automático foi removido para impedir instalação/desinstalação de APKs no dispositivo do utilizador.
