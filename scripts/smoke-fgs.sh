#!/usr/bin/env bash
# smoke-fgs.sh — quick foreground-service smoke test.
#
#   launch -> start tracking (moon) -> confirm SleepTrackingService is up and the
#   process didn't crash -> stop (Wake Up) -> confirm it stopped -> clean the junk
#   session it created.
#
# Usage: smoke-fgs.sh [--duration 20] [--no-cleanup]
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

DURATION=20
CLEANUP=1
[ "${1:-}" = "--duration" ] && { DURATION="${2:-20}"; shift 2; }
[ "${1:-}" = "--no-cleanup" ] && CLEANUP=0

wake_and_unlock || exit 1
launch_app
tap_moon || exit 1

echo "== tracking ${DURATION}s, then checking FGS =="
sleep "$DURATION"

SVC=$(adb shell dumpsys activity services "$PKG" 2>/dev/null | grep -c 'SleepTrackingService' || true)
echo "SleepTrackingService refs: $SVC"
[ "$SVC" -ge 1 ] && echo "PASS: FGS running" || { echo "FAIL: FGS not running"; exit 1; }

echo "== crash check =="
if adb logcat -d 2>/dev/null | grep -qE "FATAL EXCEPTION.*$PKG|AndroidRuntime.*$PKG"; then
    echo "FAIL: crash detected"
    exit 1
else
    echo "PASS: no crash"
fi

tap_wake_up || exit 1
sleep 8
SVC=$(adb shell dumpsys activity services "$PKG" 2>/dev/null | grep -c 'SleepTrackingService' || true)
echo "after stop — SleepTrackingService refs: $SVC"
[ "$SVC" = "0" ] && echo "PASS: FGS stopped" || echo "WARN: service still listed"

if [ "$CLEANUP" = "1" ]; then
    echo "== cleaning the junk session =="
    "$(dirname "${BASH_SOURCE[0]}")/cleanup-junk-sessions.sh" --yes
fi
echo "Smoke test done."
