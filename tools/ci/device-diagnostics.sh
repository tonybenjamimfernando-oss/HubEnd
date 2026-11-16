#!/usr/bin/env bash
# Diagnóstico do dispositivo Android durante os testes instrumentados.
# Executado dentro do passo reactivecircus/android-emulator-runner (working-directory: android).
# O diagnóstico nunca decide o resultado: só os testes determinam sucesso ou falha.
set -uo pipefail

DIAG_DIR="${DIAG_DIR:-../build/device-diagnostics}"
mkdir -p "$DIAG_DIR"

snapshot() {
  local name="$1"
  local status="$2"
  {
    echo "exit_code=$status"
    adb root
    adb wait-for-device
    adb shell cat /proc/meminfo
    adb logcat -b crash -d -v threadtime
    adb shell ls -la /data/tombstones/
    adb shell dumpsys activity exit-info dev.hubend.app
    adb shell dumpsys meminfo dev.hubend.app
  } > "$DIAG_DIR/$name.txt" 2>&1 || true

  adb wait-for-device > /dev/null 2>&1 || true
  adb logcat -b all -d -v threadtime > "$DIAG_DIR/logcat-$name.txt" 2>&1 || true
  adb logcat -b events -d -v threadtime 2>/dev/null | grep -iE 'am_crash|am_anr|am_kill' > "$DIAG_DIR/crash-anr-kill-$name.txt" 2>&1 || true
  adb shell dumpsys activity activities > "$DIAG_DIR/activity-state-$name.txt" 2>&1 || true
  adb shell dumpsys activity processes 2>/dev/null | grep -A 20 -i hubend > "$DIAG_DIR/hubend-processes-$name.txt" 2>&1 || true
  adb shell dmesg 2>/dev/null | tail -200 > "$DIAG_DIR/dmesg-$name.txt" 2>&1 || true
  adb root > "$DIAG_DIR/adb-root-$name.txt" 2>&1 || true
  printf 'ADB_ROOT_STATUS(%s): %s\n' "$name" "$(tr '\n' ' ' < "$DIAG_DIR/adb-root-$name.txt" | cut -c1-160)"
}

finish() {
  local status=$?
  echo "::group::Diagnóstico do dispositivo"
  snapshot after "$status"
  find app/build/outputs/androidTest-results/connected -type f -print > "$DIAG_DIR/android-test-result-files.txt" 2>&1 || true
  find hubops/build/outputs/androidTest-results/connected -type f -print > "$DIAG_DIR/hubops-android-test-result-files.txt" 2>&1 || true
  echo "::endgroup::"
  exit "$status"
}
trap finish EXIT

chmod +x gradlew
snapshot before "boot"

echo "::group::Testes instrumentados — app"
./gradlew --no-daemon :app:connectedDebugAndroidTest
app_status=$?
echo "app_exit=$app_status"
echo "::endgroup::"

echo "::group::Testes instrumentados — hubops"
./gradlew --no-daemon :hubops:connectedDebugAndroidTest -PhubendDeviceTests
hubops_status=$?
echo "hubops_exit=$hubops_status"
echo "::endgroup::"

if [ "$app_status" -ne 0 ] || [ "$hubops_status" -ne 0 ]; then
  echo "::error::Testes instrumentados falharam (app=$app_status, hubops=$hubops_status)"
  exit 1
fi
echo "Testes instrumentados concluídos sem falhas."
