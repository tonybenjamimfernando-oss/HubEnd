#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
echo '=== HUBOPS / SNSE reproducible preparation ==='
python3 -m pip install --upgrade requests torch transformers peft accelerate safetensors
if [[ ! -s data/cve_pairs.jsonl ]]; then
  python3 tools/snse/fetch_cve_dataset.py --out data/cve_pairs.jsonl
fi
python3 tools/snse/finetune_shards.py --data data/cve_pairs.jsonl --out models/finetuned --merge
make -f tools/snse/Makefile quantize
make -f tools/snse/Makefile android-arm64
if [[ -z "${SNSE_SIGNING_KEY:-}" ]]; then echo "SNSE_SIGNING_KEY is required to package verified models" >&2; exit 2; fi
make -f tools/snse/Makefile install-to-apk package-models
printf '\nModels prepared. This does NOT certify accuracy, memory, latency, or Android inference.\n'
