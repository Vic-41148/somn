#!/usr/bin/env bash
# verify-wake-window.sh — fires an alarm through the REAL firing path and asserts the
# WAKE-01 wake-confirmation window honors the per-alarm wakeWindowMinutes.
#
# Why this exists: the per-alarm wake window used to be decorative — the firing service read
# only the global wakeVerificationWindowSeconds preference (default 15s), so the 10-45min
# smart-wake window the user set on the edit screen never reached the firing path. That wiring
# landed in commit 49a5112. This test fires an alarm with wake_window_minutes=2 (via the same
# intent extras AlarmReceiver forwards) and asserts the post-dismiss "Confirm within" countdown
# is minutes-scale (~2m), not the 15s global default.
#
# It also exercises the per-alarm captcha precedence: firing with captcha_type=MATH must show
# the math task (per-alarm beats the global preference), and the script solves it so Dismiss
# unlocks — the whole dismissal flow is real, no shortcuts.
#
# Requires adb root (Android 14+ won't let the shell uid start a non-exported foreground
# service; on a userdebug emulator `adb root` sidesteps that). The service is non-exported by
# design, so this script is deliberately emulator/root-only — it is a device e2e, not something
# a CI job without a device can run.
#
# Gotchas baked in:
#  - The fire must use start-foreground-service (that's the real AlarmReceiver path), but a
#    cold app process can blow the 5s startForeground watchdog on a slow emulator — so the app
#    is launched and settled first, and a dead process mid-fire triggers a relaunch + refire.
#  - Cleanup uses plain `am start-service -a DISMISS`, NOT start-foreground-service: the
#    DISMISS branch never calls startForeground() (it stops the ring and runs a silent backup
#    before stopSelf), which blows the FGS watchdog and crashes the app.
#  - The math captcha can be unsolvable from adb: num1 (10..49) minus num2 (1..19) goes
#    negative, and the Number-keyboard input filter drops the minus sign — so a rejected
#    answer ("Incorrect, try again") re-fires for a fresh problem instead of failing.
#
# Usage: verify-wake-window.sh [--minutes N]
#   --minutes N   per-alarm wake window to assert, in minutes (default 2). Any value the
#                 firing path should honor works — e.g. 10/30/45 match the edit-screen
#                 slider range. The countdown assertion scales with N automatically.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

WAKE_MINUTES=2        # per-alarm wake window we fire with (well above the 15s global default)
while [ $# -gt 0 ]; do
    case "$1" in
        --minutes)
            [ $# -ge 2 ] || { echo "FAIL: --minutes needs a value"; exit 1; }
            WAKE_MINUTES=$2
            shift 2
            ;;
        *)
            echo "FAIL: unknown argument '$1' (usage: verify-wake-window.sh [--minutes N])"
            exit 1
            ;;
    esac
done
if [[ ! "$WAKE_MINUTES" =~ ^[0-9]+$ ]] || [ "$WAKE_MINUTES" -lt 1 ]; then
    echo "FAIL: --minutes must be a positive integer (got '$WAKE_MINUTES')"
    exit 1
fi
FAKE_ALARM_ID=999999  # no such alarm in the DB -> the service skips persistence side-effects
SVC=dev.vic41148.somn/dev.vic41148.somn.feature.alarm.service.AlarmService
LOG="/tmp/wake-window-$$.logcat"  # $$ so concurrent runs can't clobber each other's evidence

# Android 14+ blocks shell starts of non-exported services; the emulator's userdebug adbd
# restarts as root so the am calls below can reach the (correctly non-exported) AlarmService.
adb root >/dev/null 2>&1 || true
adb wait-for-device

echo "== firing alarm with wake_window_minutes=$WAKE_MINUTES (per-alarm MATH captcha) =="
wake_and_unlock || exit 1
launch_app
adb logcat -c 2>/dev/null || true
logcat_start "$LOG"

# Stop anything left ringing by a previous run (start-service: no FGS obligation).
adb shell am start-service -a DISMISS -n "$SVC" >/dev/null 2>&1 || true
sleep 2

# If a previous crash dialog is still up, clear it with a fresh launch.
if adb shell dumpsys window 2>/dev/null | grep -q 'Application Error'; then
    echo "  clearing leftover crash dialog"
    adb shell am force-stop "$PKG" >/dev/null 2>&1 || true
    sleep 2
    launch_app
fi

fire_alarm() {
    adb shell am start-foreground-service \
        -n "$SVC" \
        --ei alarm_id "$FAKE_ALARM_ID" \
        --es alarm_label WAKE_E2E \
        --ez vibration true \
        --ei gradual_seconds 60 \
        --es captcha_type MATH \
        --ei wake_window_minutes "$WAKE_MINUTES" >/dev/null 2>&1
}

solve_ok=0
for TRY in 1 2 3; do
    echo "  ── attempt $TRY: fire + solve ──"
    if [ "$TRY" -gt 1 ]; then
        # Rejected attempt: stop the ringing alarm and fire a fresh one (each firing
        # regenerates the captcha problem via reset()).
        adb shell am start-service -a DISMISS -n "$SVC" >/dev/null 2>&1 || true
        sleep 2
        fire_alarm
    else
        fire_alarm
    fi

    # Wait for the math captcha to appear on the firing surface. If the app process died
    # (e.g. FGS watchdog on a cold start), relaunch and refire once.
    # Note: the grep pipeline is pipefail-safe because head -1 masks grep's no-match exit.
    Q=""
    REFIRED=0
    for _ in $(seq 1 15); do
        D=$(ui_dump)
        Q=$(adb shell cat "$D" 2>/dev/null | grep -oE 'text="[0-9]+ [+*-] [0-9]+ = \?"' | head -1)
        [ -n "$Q" ] && break
        if [ "$REFIRED" = 0 ] && ! adb shell pidof "$PKG" >/dev/null 2>&1; then
            echo "    app process died mid-fire — relaunching and refiring once"
            launch_app
            fire_alarm
            REFIRED=1
        fi
        sleep 2
    done
    if [ -z "$Q" ]; then
        echo "FAIL: math captcha never appeared on the firing screen (fire_alarm / adb root OK?)"
        exit 1
    fi
    echo "  captcha question: $Q"

    # Parse A op B and compute the answer.
    Q="${Q#text=\"}"; Q="${Q%\"}"
    if [[ "$Q" =~ ([0-9]+)\ ([+*-])\ ([0-9]+) ]]; then
        A=${BASH_REMATCH[1]}; OP=${BASH_REMATCH[2]}; B=${BASH_REMATCH[3]}
        case "$OP" in
            +) ANS=$((A + B));;
            -) ANS=$((A - B));;
            *) ANS=$((A * B));;
        esac
    else
        echo "FAIL: could not parse captcha question '$Q'"; exit 1
    fi
    echo "  answer: $A $OP $B = $ANS"

    C=$(find_center 'text="Result"' "$(ui_dump)")
    [ -z "$C" ] && { echo "FAIL: Result field not found"; exit 1; }
    tap_at "$C"
    sleep 1
    adb shell input text "$ANS"
    sleep 1
    # Focusing the field pops the soft keyboard, which covers the Submit button — close it first.
    adb shell input keyevent KEYCODE_BACK
    sleep 1
    C=""
    for _ in $(seq 1 5); do
        C=$(find_center 'text="Submit"' "$(ui_dump)")
        [ -n "$C" ] && break
        sleep 1
    done
    [ -z "$C" ] && { echo "FAIL: Submit button not found (keyboard not closed?)"; exit 1; }
    tap_at "$C"
    sleep 2

    # A rejected answer (negative value the Number filter dropped, or a missed keystroke)
    # shows "Incorrect, try again" — re-fire for a fresh problem rather than fail.
    if ui_texts "$(ui_dump)" | grep -q 'Incorrect, try again'; then
        echo "  answer rejected (likely a negative the Number field can't type) — retrying"
        continue
    fi
    solve_ok=1
    break
done
[ "$solve_ok" = 1 ] || { echo "FAIL: could not solve the captcha after 3 re-fires"; exit 1; }

# ── captcha solved -> Dismiss unlocks; tap it to enter WAKE-01 ────────────────
C=""
for _ in $(seq 1 6); do
    D=$(ui_dump)
    # Button reads "Locked" while the captcha is unsolved and "Dismiss" once solved.
    C=$(find_center 'text="Dismiss"' "$D")
    [ -n "$C" ] && break
    sleep 1.5
done
[ -z "$C" ] && { echo "FAIL: Dismiss never unlocked (captcha not solved?)"; exit 1; }
echo "  tapping Dismiss"
tap_at "$C"

# The phase transition (FIRING -> AWAITING_WAKE_CONFIRMATION) is an animated crossfade;
# poll for the countdown instead of assuming a fixed sleep survives a slow emulator.
LABEL=""
for _ in $(seq 1 10); do
    D=$(ui_dump)
    LABEL=$(ui_texts "$D" | grep -oE 'Confirm within [0-9]+(m [0-9]+s|m|s)' | head -1 || true)
    [ -n "$LABEL" ] && break
    sleep 1.5
done
if [ -z "$LABEL" ]; then
    echo "FAIL: no 'Confirm within' countdown after Dismiss (wake verification off?)"
    exit 1
fi
echo "  countdown label: $LABEL"

MINS=$(echo "$LABEL" | grep -oE '[0-9]+m' | grep -oE '[0-9]+' || true)
SECS=$(echo "$LABEL" | grep -oE '[0-9]+s' | grep -oE '[0-9]+' || true)
TOTAL=$(( ${MINS:-0} * 60 + ${SECS:-0} ))

EXPECTED_MIN=$(( WAKE_MINUTES * 60 ))
if [ "$TOTAL" -gt 60 ] && [ "$TOTAL" -le "$EXPECTED_MIN" ]; then
    echo "PASS: WAKE-01 window honors per-alarm wakeWindowMinutes ($TOTAL s, expected ~${EXPECTED_MIN}s, global pref is 15s)"
else
    echo "FAIL: WAKE-01 window is $TOTAL s — expected minutes-scale (~${EXPECTED_MIN}s); the per-alarm window is not in the firing path"
    exit 1
fi

# ── cleanup: hard-dismiss (bypasses the wake window) so nothing keeps ringing ──
adb shell am start-service -a DISMISS -n "$SVC" >/dev/null 2>&1 || true
sleep 3

logcat_stop
if grep -q 'FATAL EXCEPTION' "$LOG"; then
    echo "FAIL: FATAL exception in logcat during the alarm-firing flow"
    exit 1
fi
echo "PASS: no FATAL in logcat"
exit 0
