#!/usr/bin/env bash
# Development identities only. Never commit the generated private keys.
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p config dev-private tls
for id in peer-1 peer-2 peer-3; do
  openssl genpkey -algorithm ED25519 -out "dev-private/${id}.key.pem"
  openssl pkey -in "dev-private/${id}.key.pem" -pubout -outform DER -out "dev-private/${id}.pub.der"
done
python3 - <<'PY'
import base64,json
from pathlib import Path
keys={}
for p in sorted(Path('dev-private').glob('*.pub.der')):
 keys[p.name.split('.')[0]]=base64.b64encode(p.read_bytes()).decode()
Path('config/peer-keys.json').write_text(json.dumps(keys,indent=2)+'\n')
PY
cat > tls/openssl.cnf <<'CNF'
[req]
distinguished_name=dn
x509_extensions=v3
prompt=no
[dn]
CN=GoPA development cluster
[v3]
subjectAltName=@alt
keyUsage=digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth
[alt]
DNS.1=peer-1
DNS.2=peer-2
DNS.3=peer-3
DNS.4=localhost
IP.1=127.0.0.1
CNF
openssl req -x509 -newkey rsa:3072 -nodes -days 30 -keyout tls/tls.key -out tls/tls.crt -config tls/openssl.cnf
chmod 600 dev-private/*.key.pem tls/tls.key
printf 'Development peer identities and TLS certificate generated. Install the development CA/cert only in isolated test clients.\n'
