#!/usr/bin/env python3
"""Ed25519-sign each GGUF and then sign a manifest containing each signature."""
import base64, json, subprocess, sys
from pathlib import Path
root, key = Path(sys.argv[1]), sys.argv[2]
manifest = root / "manifest.json"
if not manifest.is_file(): raise SystemExit("manifest.json missing; run write_model_manifest.py first")
data = json.loads(manifest.read_text())
for shard, row in sorted(data.get("shards", {}).items()):
    path = root / row["file"]
    if not path.is_file() or path.is_symlink(): raise SystemExit(f"missing/unsafe model: {path}")
    sig_path = Path(str(path) + ".sig")
    subprocess.run(["openssl", "pkeyutl", "-sign", "-rawin", "-inkey", key,
                    "-in", str(path), "-out", str(sig_path)], check=True)
    row["signatureBase64"] = base64.b64encode(sig_path.read_bytes()).decode("ascii")
manifest.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n")
subprocess.run(["openssl", "pkeyutl", "-sign", "-rawin", "-inkey", key,
                "-in", str(manifest), "-out", str(manifest) + ".sig"], check=True)
print(f"Signed {len(data.get('shards', {}))} model shards and manifest")
