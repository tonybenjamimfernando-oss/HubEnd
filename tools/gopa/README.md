# GoPA v2 — cluster e protocolo

## Cluster de desenvolvimento

1. `./generate-dev-identity.sh` cria três pares Ed25519, `config/peer-keys.json` e um certificado TLS de teste com SAN para `peer-1`, `peer-2`, `peer-3` e `localhost`.
2. Define um token temporário: `export GOPA_API_TOKEN="$(openssl rand -hex 32)"`.
3. `docker compose up --build -d`.
4. Verifica cada peer com `curl --cacert tls/tls.crt https://localhost:8443/health` (portas 8443, 8444 e 8445).
5. `./test_gopa.sh` executa os testes unitários/race e valida a configuração Compose.
6. `docker compose down` termina o cluster; os named volumes preservam os ledgers.

O Compose é **apenas laboratório local** e executa como root para ler a chave de teste com permissões 0600. O Dockerfile de produção usa UID 10001. Em produção, usar certificados CA reais, segredo de transporte gerido fora do Compose, identidade/credencial própria por peer, rotação e revogação de chaves e volumes com permissões corretas.

## Protocolo Android ↔ Go

O cliente Android e o servidor assinam/verificam a mesma mensagem UTF-8, separada por `\n`:

```text
GOPA-ATTESTATION-V1
<artifactHash lowercase>
<PASS|FAIL|SUSPICIOUS>
<evidenceRoot lowercase>
<issuedAtEpochMs decimal>
<peerId>
<evidenceHashes lowercase separados por vírgula>
```

A assinatura é Ed25519 em Base64 standard. A raiz Merkle usa SHA-256 com prefixo `0x01` nos nós internos; folha única é o próprio hash da evidência. O servidor exige que o peer esteja no registo de chaves e que a assinatura corresponda à chave pública X.509/DER desse peer.

O quórum é `floor(2N/3)+1`, calculado sobre N peers elegíveis registados, nunca sobre o número de votos que chegaram. Uma identidade só pode emitir um recibo imutável por artefacto. Isso não transforma o serviço num protocolo BFT geral.
