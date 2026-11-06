#!/usr/bin/env bash
# Device smoke test for the packaged arm64 PRoot + Alpine base image.
set -euo pipefail
ADB="${ADB:-adb}"; PKG="${PKG:-dev.hubops.app}"
command -v "$ADB" >/dev/null
echo '=== NSC / PRoot device smoke test ==='
"$ADB" shell run-as "$PKG" sh -c 'test -x files/native/proot && test -f files/oci/rootfs/alpine_3.24.4/.ready'
run() { "$ADB" shell run-as "$PKG" sh -c "files/native/proot -r files/oci/rootfs/alpine_3.24.4 /bin/sh -c '$1'"; }
PASS=0; FAIL=0
for cmd in 'echo NSC_OK' 'test -d /etc' 'test -x /bin/sh' 'cat /etc/alpine-release' 'env' 'ls /'; do
  if run "$cmd" >/dev/null 2>&1; then echo "PASS $cmd"; PASS=$((PASS+1)); else echo "FAIL $cmd"; FAIL=$((FAIL+1)); fi
done
if run 'test ! -e /system' >/dev/null 2>&1; then echo 'PASS /system absent in rootfs view'; PASS=$((PASS+1)); else echo 'FAIL /system visible'; FAIL=$((FAIL+1)); fi
echo "Result: $PASS passed, $FAIL failed"
[[ "$FAIL" -eq 0 ]]
