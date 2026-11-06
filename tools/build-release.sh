#!/usr/bin/env bash
# Full release preparation. Training is intentionally a host/GPU task, not an Android task.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to NDK r28+}"
: "${OMEGA_SIGNING_KEY:?Set OMEGA_SIGNING_KEY to an Ed25519 PEM private key}"
NDK_MAJOR="$(sed -n 's/^Pkg.Revision = \([0-9][0-9]*\).*/\1/p' "$ANDROID_NDK_HOME/source.properties" | head -1)"
[[ "${NDK_MAJOR:-0}" -ge 28 ]] || { echo "Android NDK r28+ is required for 16 KB ELF alignment" >&2; exit 2; }
export SNSE_SIGNING_KEY="$OMEGA_SIGNING_KEY"
export NSC_SIGNING_KEY="$OMEGA_SIGNING_KEY"
python3 -m pip install --upgrade requests torch transformers peft accelerate safetensors
export OMEGA_RELEASE_PUBLIC_KEY_B64="$(openssl pkey -in "$OMEGA_SIGNING_KEY" -pubout -outform DER | base64 -w0)"

if [[ ! -s data/cve_pairs.jsonl ]]; then
  python3 tools/snse/fetch_cve_dataset.py --out data/cve_pairs.jsonl
fi
python3 tools/snse/finetune_shards.py --data data/cve_pairs.jsonl --out models/finetuned --merge
make -f tools/snse/Makefile quantize
test -s data/test_held_out.jsonl || { echo 'data/test_held_out.jsonl with at least 100 independent cases is required' >&2; exit 2; }
python3 tools/snse/eval_precision.py --test data/test_held_out.jsonl --shards models/shards --llama-cli build/snse/host/bin/llama-cli --limit 100 --threshold-precision 0.85 --threshold-recall 0.75
make -f tools/snse/Makefile android-arm64
go -C tools/gopa test -race -count=1 ./...
python3 -m py_compile tools/snse/*.py tools/nsc/*.py
make -f tools/snse/Makefile install-to-apk package-models
make -f tools/nsc/Makefile sign-manifest

# HubEnd: a interface e o shell Capacitor são preparados antes do Gradle.
bun install --frozen-lockfile
bunx cap sync android
(cd android && ./gradlew --no-daemon clean :hubops:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug)
mkdir -p dist
cp android/app/build/outputs/apk/debug/app-debug.apk dist/HubEnd-debug.apk
sha256sum dist/HubEnd-debug.apk dist/snse-models-arm64.zip android/hubops/src/main/assets/oci/release-manifest.json
