#!/usr/bin/env python3
import hashlib, json
from pathlib import Path
root=Path('android/hubops/src/main/assets')
entries={}
for name in ('native/arm64-v8a/proot','native/arm64-v8a/llama-cli','oci/alpine_3.24.4.tar.gz'):
 p=root/name
 if not p.is_file() or p.is_symlink(): raise SystemExit(f'missing safe artifact: {p}')
 h=hashlib.sha256()
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
 entries[name]={'sha256':h.hexdigest(),'sizeBytes':p.stat().st_size}
out=root/'oci/release-manifest.json';out.parent.mkdir(parents=True,exist_ok=True)
out.write_text(json.dumps({'schema':1,'artifacts':entries},indent=2)+'\n')
print(out)
