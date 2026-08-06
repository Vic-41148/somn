#!/usr/bin/env bash
# run-release-preflight.sh — full release preflight on the emulator, in one command.
#
#   bash scripts/run-release-preflight.sh            # boot + run all 15 device scripts
#   bash scripts/run-release-preflight.sh --boot-only  # just ensure the emulator, exit
#   bash scripts/run-release-preflight.sh --avd other  # use a different AVD
#
# Why this wrapper exists (two hard-won lessons):
#
#  1. The emulator must be launched DETACHED (setsid) — `nohup ... &` alone dies with the
#     launching shell's process group, and a preflight left staring at zero devices hangs
#     forever on an `adb wait-for-device`. setsid detaches the emulator so it survives the
#     shell that spawned it.
#  2. The device scripts are tuned against a 1080x2340 phone skin. The somn_test AVD's
#     default display is a tiny 320x640, and at that size every UI element lookup in the
#     verify scripts misses (History cards reflow, the Home moon button drops off-screen,
#     scrolled sections never match). The runner boots with -skin 1080x2340 and *reuses a
#     running emulator only if its display already matches* — a wrong-size emulator is
#     killed and rebooted rather than silently producing a wall of bogus FAILs.
#
# The 15-step sequence itself is unchanged from the historical preflight: destructive
# seeders first, then state-dependent verifies, the cycling re-seeding session e2e, a
# baseline restore, then the build-only pipeline check. Steps MUST run sequentially — they
# share one device and fight each other if parallelised.
set -uo pipefail

AVD="somn_test"
BOOT_ONLY=false
while [ $# -gt 0 ]; do
    case "$1" in
        --avd)        AVD="${2:?--avd needs an argument}"; shift 2 ;;
        --boot-only)  BOOT_ONLY=true; shift ;;
        -h|--help)
            echo "usage: run-release-preflight.sh [--avd <name>] [--boot-only]" >&2
            exit 0 ;;
        *) echo "unknown argument: $1 (see --help)" >&2; exit 1 ;;
    esac
done

export PATH="$PATH:${ANDROID_HOME:-$ANDROID_SDK_ROOT}/platform-tools"
export ANDROID_SERIAL="${ANDROID_SERIAL:-emulator-5554}"
SERIAL="$ANDROID_SERIAL"
SKIN="1080x2340"
BOOT_TIMEOUT_SECS=300
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EMU="${ANDROID_HOME:-$ANDROID_SDK_ROOT}/emulator/emulator"

adb start-server >/dev/null 2>&1 || true

# ── emulator bootstrap ──────────────────────────────────────────────────────────
ensure_emulator() {
    local booted="" size=""
    if adb -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | grep -q 1; then
        booted=1
        size="$(adb -s "$SERIAL" shell wm size 2>/dev/null | grep -oE '[0-9]+x[0-9]+' | head -1)"
    fi
    if [ -n "$booted" ] && [ "$size" = "$SKIN" ]; then
        echo "Reusing running emulator $SERIAL (display $size matches the $SKIN skin)."
        return 0
    fi
    if [ -n "$booted" ]; then
        echo "Emulator $SERIAL is up but at $size (scripts expect $SKIN) — rebooting with the phone skin."
        adb -s "$SERIAL" emu kill 2>/dev/null || true
        pkill -f "avd $AVD" 2>/dev/null || true
        sleep 5
    fi
    [ -x "$EMU" ] || { echo "FATAL: emulator binary not found at $EMU (set ANDROID_HOME/ANDROID_SDK_ROOT)" >&2; exit 1; }
    "$EMU" -list-avds 2>/dev/null | grep -qx "$AVD" || {
        echo "FATAL: AVD '$AVD' not found. Available: $("$EMU" -list-avds 2>/dev/null | tr '\n' ' ')" >&2
        exit 1
    }
    echo "Booting $AVD headless with the $SKIN skin (detached via setsid)..."
    setsid nohup "$EMU" -avd "$AVD" -no-window -no-audio -no-boot-anim \
        -gpu swiftshader_indirect -no-snapshot -no-metrics -skin "$SKIN" \
        > /tmp/somn-emu-boot.log 2>&1 < /dev/null &
    disown 2>/dev/null || true
    local waited=0
    until adb -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | grep -q 1; do
        sleep 5
        waited=$((waited + 5))
        if [ "$waited" -ge "$BOOT_TIMEOUT_SECS" ]; then
            echo "FATAL: emulator did not boot within ${BOOT_TIMEOUT_SECS}s" >&2
            tail -10 /tmp/somn-emu-boot.log >&2
            exit 1
        fi
    done
    local now_size
    now_size="$(adb -s "$SERIAL" shell wm size 2>/dev/null | grep -oE '[0-9]+x[0-9]+' | head -1)"
    [ "$now_size" = "$SKIN" ] || {
        echo "FATAL: emulator booted at $now_size, expected $SKIN" >&2
        exit 1
    }
    echo "Emulator booted after ~${waited}s at $now_size."
}

ensure_emulator

if [ "$BOOT_ONLY" = true ]; then
    echo "Emulator ready on $SERIAL at $(adb -s "$SERIAL" shell wm size | tr -d '\r')."
    exit 0
fi

# ── the 15-step preflight ───────────────────────────────────────────────────────
cd /home/vic/Projects/somn || { echo "FATAL: repo dir missing" >&2; exit 1; }
LOG=/tmp/preflight-$$.log
: > "$LOG"

trap 'echo "INTERRUPTED — per-step log: $LOG"; exit 130' INT TERM

declare -a NAMES RESULTS TIMES
pass=0; fail=0

run() {  # run <name> <cmd...>
    local name="$1"; shift
    local start end rc
    start=$(date +%s)
    echo ""
    echo "############################################################"
    echo "### [$name]"
    echo "############################################################"
    "$@" >> "$LOG" 2>&1
    rc=$?
    end=$(date +%s)
    NAMES+=("$name")
    if [ "$rc" -eq 0 ]; then
        RESULTS+=("PASS"); pass=$((pass+1))
    else
        RESULTS+=("FAIL"); fail=$((fail+1))
    fi
    TIMES+=("$((end-start))s")
    echo "[$name] exit=$rc  ($((end-start))s)  -> ${RESULTS[${#RESULTS[@]}-1]}"
}

# ── 1. destructive baseline seed (MALE) ──────────────────────────────────────
run "reset-demo default"       "$SCRIPT_DIR/reset-demo.sh" default
# ── 2. state-dependent verifies on the MALE baseline ─────────────────────────
run "verify-db-phase"          "$SCRIPT_DIR/verify-db-phase.sh"
run "verify-cycle-legend male" "$SCRIPT_DIR/verify-cycle-legend.sh" male
run "verify-vitals"            "$SCRIPT_DIR/verify-vitals.sh"
run "verify-alarms"            "$SCRIPT_DIR/verify-alarms.sh"
run "verify-habits-forms"      "$SCRIPT_DIR/verify-habits-forms.sh"
run "verify-trends"            "$SCRIPT_DIR/verify-trends.sh"
run "verify-wake-window"       "$SCRIPT_DIR/verify-wake-window.sh"
# ── 3. FGS smoke + junk cleanup ──────────────────────────────────────────────
run "smoke-fgs"                "$SCRIPT_DIR/smoke-fgs.sh"
run "cleanup-junk-sessions"    "$SCRIPT_DIR/cleanup-junk-sessions.sh" --yes
# ── 4. THE morning-alert e2e (re-seeds CYCLING, asserts Luteal alert) ────────
run "run-session-e2e"          "$SCRIPT_DIR/run-session-e2e.sh"
run "verify-db-phase LUTEAL"   "$SCRIPT_DIR/verify-db-phase.sh" --expect LUTEAL
run "verify-cycle-legend cycling" "$SCRIPT_DIR/verify-cycle-legend.sh" cycling
# ── 5. restore baseline as the final device state ────────────────────────────
run "reset-demo default (final)" "$SCRIPT_DIR/reset-demo.sh" default
# ── 6. build-only release pipeline check (no device needed) ──────────────────
run "verify-release-pipeline"  "$SCRIPT_DIR/verify-release-pipeline.sh"

# ── matrix ────────────────────────────────────────────────────────────────────
echo ""
echo "============================================================"
echo " PREFLIGHT MATRIX"
echo "============================================================"
printf "%-28s %-5s %s\n" "SCRIPT" "RESULT" "TIME"
printf -- "----------------------------------------------\n"
for i in "${!NAMES[@]}"; do
    printf "%-28s %-5s %s\n" "${NAMES[$i]}" "${RESULTS[$i]}" "${TIMES[$i]}"
done
printf -- "----------------------------------------------\n"
printf "TOTAL: %d  PASS: %d  FAIL: %d\n" "${#NAMES[@]}" "$pass" "$fail"
echo ""
echo "Full per-step output: $LOG"
[ "$fail" -eq 0 ]
