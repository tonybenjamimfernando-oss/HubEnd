#!/usr/bin/env python3
"""Create deterministic SHA-256 manifest for GGUF shard artifacts."""
import hashlib, json, sys
from pathlib import Path

root = Path(sys.argv[1] if len(sys.argv) > 1 else "models/shards")
expected = {"sqli", "crypto", "auth", "network", "storage", "logging", "deserialization"}
items = {}
for name in sorted(expected):
    p = root / f"{name}.gguf"
    if not p.is_file() or p.is_symlink() or p.stat().st_size == 0:
        continue
    h = hashlib.sha256()
    with p.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    items[name] = {"file": p.name, "sizeBytes": p.stat().st_size, "sha256": h.hexdigest()}
if not items:
    raise SystemExit("No valid GGUF shards found")
(root / "manifest.json").write_text(json.dumps({"schema": 1, "shards": items}, indent=2) + "\n")
print(f"Manifest: {len(items)}/7 shards")
