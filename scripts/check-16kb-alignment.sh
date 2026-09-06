#!/usr/bin/env bash
# Fails if any 64-bit .so inside the APK has a LOAD segment below 16KB alignment.
# 32-bit ABIs are exempt (the 16KB requirement targets 64-bit). RELRO's Align field
# is routinely 0x1 even in compliant libs — LOAD segments are the ground truth.
set -euo pipefail
APK="${1:?usage: check-16kb-alignment.sh <apk>}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
unzip -q -o "$APK" 'lib/*/*.so' -d "$WORK"
FAIL=0
while IFS= read -r SO; do
  case "$SO" in
    */armeabi-v7a/*|*/x86/*) continue ;;
  esac
  WORST=$(readelf -l -W "$SO" | awk '/LOAD/{print $NF}' | sort | head -1)
  # awk prints hex like 0x4000; compare numerically.
  if [ "$((WORST))" -lt "$((0x4000))" ]; then
    echo "UNALIGNED: $SO (worst LOAD align $WORST)"
    FAIL=1
  else
    echo "OK: ${SO#"$WORK"/} ($WORST)"
  fi
done < <(find "$WORK" -name '*.so')
exit "$FAIL"
