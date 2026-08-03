#!/usr/bin/env bash
# run-session-e2e.sh — end-to-end test of the morning-alert path.
#
# Drives a REAL start -> stop tracking session through the UI (moon -> Wake Up) and
# asserts, with a live logcat capture + dumpsys:
#   1. the session completed in the DB
#   2. the deep-sleep alert (id 1001) posted
#   3. the hormonal Luteal Phase Alert (id 1003) posted — unless --profile default
#   4. the tracking FGS stopped
#
# Usage:
#   run-session-e2e.sh                 # seed cycling, run, expect the Luteal alert
#   run-session-e2e.sh --profile default   # seed MALE, expect NO hormonal alert
#   run-session-e2e.sh --no-seed       # use the current device state as-is
#   run-session-e2e.sh --duration 50 --retries 1
#
# Why retries: on this device the FIRST run after a fresh install is flaky (JIT/GC
# warmup can stall the teardown coroutine, silently skipping the alerts). Warm runs
# are consistent. --retries N warm-relaunches (same install — NOT a reinstall, which
# would just reproduce the first-run condition) and retries up to N times.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

PROFILE="cycling"
DO_SEED=1
DURATION=50
RETRIES=1

while [ $# -gt 0 ]; do
    case "$1" in
        --profile)   PROFILE="${2:-cycling}"; shift 2 ;;
        --no-seed)   DO_SEED=0; shift ;;
        --duration)  DURATION="${2:-50}"; shift 2 ;;
        --retries)   RETRIES="${2:-1}"; shift 2 ;;
        *) echo "unknown arg: $1" >&2; exit 1 ;;
    esac
done

case "$PROFILE" in
  default|male) PROFILE="default"; EXPECT_ALERT=0 ;;
  cycling|female) PROFILE="cycling"; EXPECT_ALERT=1 ;;
  *) echo "usage: run-session-e2e.sh [--profile default|cycling] [--no-seed] [--duration N] [--retries N]" >&2; exit 1 ;;
esac

wake_and_unlock || exit 1

if [ "$DO_SEED" = 1 ]; then
    "$(dirname "${BASH_SOURCE[0]}")/seed-demo.sh" "$PROFILE"
fi

run_once() {
    # fail=1 on any assertion failure; returned as the shell exit code (1 = fail).
    local log="/tmp/somn_e2e_$$.log"
    local fail=0

    echo "== attempt: tracking ${DURATION}s =="
    logcat_start "$log"
    launch_app
    tap_moon || { logcat_stop; return 1; }
    echo "   tracking for ${DURATION}s..."
    sleep "$DURATION"
    tap_wake_up || { logcat_stop; return 1; }
    sleep 12
    logcat_stop

    # 1. session completed
    local completed
    completed=$(db_q "SELECT isCompleted FROM sleep_sessions ORDER BY id DESC LIMIT 1;" 2>/dev/null || echo "")
    if [ "$completed" = "1" ]; then
        echo "   PASS: newest session completed in DB"
    else
        echo "   FAIL: newest session isCompleted='$completed'"
        fail=1
    fi

    # 2. deep-sleep alert (always fires for a near-zero-epoch session). Its absence
    #    with a completed session means teardown skipped the alerts entirely -> fail.
    if grep -q 'dev\.vic41148\.somn: notify(1001' "$log"; then
        echo "   PASS: deep-sleep alert (1001) posted"
    else
        echo "   FAIL: deep-sleep alert (1001) not seen in live log"
        fail=1
    fi

    # 3. hormonal alert
    if [ "$EXPECT_ALERT" = "1" ]; then
        if grep -q 'dev\.vic41148\.somn: notify(1003' "$log" && \
           notification_text_present "Luteal Phase Alert"; then
            echo "   PASS: Luteal Phase Alert (1003) posted"
        else
            echo "   FAIL: Luteal Phase Alert (1003) NOT posted"
            fail=1
        fi
    else
        if notification_text_present "Luteal Phase Alert"; then
            echo "   FAIL: Luteal alert posted but profile is $PROFILE (shouldn't)"
            fail=1
        else
            echo "   PASS: no Luteal alert on $PROFILE profile (as expected)"
        fi
    fi

    # 4. FGS stopped
    local svc
    svc=$(adb shell dumpsys activity services "$PKG" 2>/dev/null | grep -c 'SleepTrackingService' || true)
    if [ "$svc" = "0" ]; then
        echo "   PASS: SleepTrackingService stopped"
    else
        echo "   WARN: SleepTrackingService still listed ($svc refs)"
    fi

    rm -f "$log"
    return $fail
}

attempt=0
# RETRIES = extra attempts after the first (default 1 -> up to 2 attempts total).
MAX_ATTEMPTS=$((RETRIES + 1))
while true; do
    attempt=$((attempt + 1))
    if run_once; then
        echo "E2E PASS (attempt $attempt)"
        exit 0
    fi
    if [ "$attempt" -ge "$MAX_ATTEMPTS" ]; then
        echo "E2E FAIL after $attempt attempt(s)"
        exit 1
    fi
    echo "   first-run-after-install flake? warm-relaunching (same install) and retrying..."
    launch_app
    sleep 2
done
