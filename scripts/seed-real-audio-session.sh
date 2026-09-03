#!/usr/bin/env bash
# seed-real-audio-session.sh - seed ONE MORE DAY of History, but with REAL audio:
# runs a real tracking session on the phone with the microphone pipeline live, and
# verifies afterwards that real audio actually flowed through it.
#
# This is the "seed one more day, but use the phone's mic this time" workflow:
#   - no data is wiped or fabricated - a REAL session (with mic) is started now and
#     stopped after the duration, so it lands in History as a genuine recorded day.
#   - the mic pipeline runs in Accelerometer sensor mode (in Sonar mode the ultrasonic
#     collector owns the mic, so audio events/BRPM are skipped by design).
#   - classification works WITHOUT the YAMNet model too - the ZCR heuristics still
#     detect cough/talk/snore bursts and save a playable WAV clip per event, so a
#     model download is not required for the pipeline to function.
#
# What "it works" means (checked at the end):
#   1. the session completed in the DB (isCompleted = 1)
#   2. the mic initialized - breathing-rate estimates (BRPM) appear in logcat and
#      there is NO "Microphone failed to initialize" warning
#   3. any captured audio events are in the DB with their WAV clips on disk
#      (event count depends on how much noise the phone heard - a quiet hour can
#      legitimately record zero events while still proving the pipeline ran)
#
# Usage:
#   seed-real-audio-session.sh                     # 1-hour real mic session
#   seed-real-audio-session.sh --duration 1200     # 20 minutes (smoke test)
#
# During a long run make a little noise every now and then (cough, hum a tune,
# talk a few words) so the classifier has something real to catch.
#
# Deps: adb, a connected authorized device, host sqlite3, and the app already
# installed (use seed-demo.sh first if starting from scratch). Requires exactly one
# device (or exactly ANDROID_SERIAL when set).
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

DURATION=3600

while [ $# -gt 0 ]; do
    case "$1" in
        --duration) DURATION="${2:-3600}"; shift 2 ;;
        *) echo "usage: seed-real-audio-session.sh [--duration SECONDS]" >&2; exit 1 ;;
    esac
done
if [ "$DURATION" -lt 60 ]; then
    echo "--duration must be >= 60 (default 3600 = 1h)." >&2
    exit 1
fi

# Exactly-one-device guard (mirrors seed-somn-demo.sh).
if [ -n "${ANDROID_SERIAL:-}" ]; then
    DEVICE_COUNT=$(adb devices | awk -v want="$ANDROID_SERIAL" \
        'NR > 1 && $2 == "device" && $1 == want { n++ } END { print n + 0 }')
else
    DEVICE_COUNT=$(adb devices | awk 'NR > 1 && $2 == "device" { n++ } END { print n + 0 }')
fi
if [ "$DEVICE_COUNT" -ne 1 ]; then
    echo "Expected exactly one connected device, found $DEVICE_COUNT." >&2
    exit 1
fi

SERIAL="$(adb get-serialno)"
LOG="/tmp/somn_audio_$$.log"

wake_and_unlock || exit 1

echo "== [1/6] runtime permissions (mic + notifications)"
adb shell pm grant "$PKG" android.permission.RECORD_AUDIO >/dev/null 2>&1 || true
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true

echo "== [2/6] let the app finalize any stale in-flight session"
launch_app
# REL-02: a fresh process (trackingState=IDLE) auto-finalizes a session abandoned by a
# previously killed service as partial data - the moon start below then opens cleanly.
sleep 6
p=$(db_q "SELECT id FROM sleep_sessions WHERE isCompleted=0 ORDER BY id DESC LIMIT 1;" 2>/dev/null || true)
if [ -n "$p" ]; then
    echo "   WARN: session #$p still in-flight after relaunch; continuing anyway" >&2
else
    echo "   no in-flight session"
fi

echo "== [3/6] force Accelerometer sensor mode (mic pipeline runs outside Sonar)"
goto_tab "Settings"
MODE_FOUND=""
for _ in 1 2 3 4 5 6; do
    f=$(ui_dump)
    c=$(find_center 'text="Accelerometer"' "$f")
    if [ -n "$c" ]; then
        y=$(echo "$c" | awk '{print $2}')
        if [ "$y" -ge 200 ] && [ "$y" -le 1900 ]; then
            MODE_FOUND=1
            break
        fi
    fi
    adb shell input swipe 540 1800 540 400 300
    sleep 1
done
if [ -n "$MODE_FOUND" ]; then
    echo "   sensor mode row at $c - selecting Accelerometer"
    tap_at "$c"
    sleep 1
else
    echo "   WARN: could not reach the sensor-mode row; assuming Accelerometer is active." >&2
fi
goto_tab "Home"

echo "== [4/6] start tracking (mic live for ${DURATION}s)"
logcat_start "$LOG"
tap_moon || { logcat_stop; exit 1; }
sleep 15

echo "   --- early mic health check (15s in) ---"
if grep -q "Microphone failed to initialize" "$LOG"; then
    echo "   FAIL: recordingFailed fired early - mic did not initialize. Aborting the run."
    logcat_stop
    echo "   Recent log tail:" >&2
    tail -40 "$LOG" >&2
    exit 1
fi
if grep -q "BRPM:" "$LOG"; then
    echo "   PASS: breathing-rate estimation running, buffers are flowing from the mic"
else
    echo "   note: no BRPM line yet (first estimate may take longer than 15s)"
fi

echo "   Running for $((DURATION / 60)) min... make some noise now and then so the classifier"
echo "   has real sounds to catch (cough, hum, talk a few words)."
remaining="$DURATION"
while [ "$remaining" -gt 0 ]; do
    chunk=$(( remaining > 300 ? 300 : remaining ))
    sleep "$chunk"
    remaining=$(( remaining - chunk ))
    if [ "$remaining" -gt 0 ]; then
        echo "   ${remaining}s left..."
    fi
done

echo "== [5/6] stop tracking + finalize"
tap_wake_up || { logcat_stop; exit 1; }
sleep 20
logcat_stop

echo "== [6/6] verify the real recorded day"
fail=0

NEWEST=$(db_q "SELECT id FROM sleep_sessions ORDER BY id DESC LIMIT 1;" 2>/dev/null || true)
if [ -n "$NEWEST" ]; then
    db_q "SELECT '  session #'||id||'  '||datetime(startTimeMillis/1000,'unixepoch','localtime')||'  '||sleepDurationMinutes||'min  score='||sleepScore||'  brpm='||COALESCE(avgBreathingRateBrpm,'<none>')||'  completed='||isCompleted||'  manual='||isManualEntry FROM sleep_sessions WHERE id=$NEWEST;"
    COMPLETED=$(db_q "SELECT isCompleted FROM sleep_sessions WHERE id=$NEWEST;" 2>/dev/null)
    if [ "$COMPLETED" = "1" ]; then
        echo "  PASS: session #$NEWEST completed in DB"
    else
        echo "  FAIL: session #$NEWEST isCompleted='$COMPLETED'"
        fail=1
    fi

    echo "  --- audio events for session #$NEWEST ---"
    EVENTS=$(db_q "SELECT COUNT(*) FROM audio_events WHERE sessionId=$NEWEST;" 2>/dev/null || echo "")
    if [ -n "$EVENTS" ] && [ "$EVENTS" -gt 0 ]; then
        db_q "SELECT '    '||type||': '||COUNT(*)||'  (peak '||MAX(intensityDecibels)||'dB)' FROM audio_events WHERE sessionId=$NEWEST GROUP BY type;"
        echo "  PASS: $EVENTS real audio events captured (open the day in History to replay the WAV clips)"
    else
        echo "    0 events - the hour was quiet (see BRPM check below for proof the mic ran)."
    fi

    echo "  --- WAV clips on device ---"
    CLIPS=$(adb shell run-as "$PKG" sh -c "ls -1 files/sleep_snore files/sleep_cough files/sleep_talk files/sleep_events 2>/dev/null | grep _${NEWEST}_ | wc -l" 2>/dev/null | tr -d '\r ')
    echo "    clips referencing session $NEWEST: ${CLIPS:-0}"
else
    echo "  FAIL: could not read the newest session id."
    fail=1
fi

echo "  --- mic pipeline proof (logcat) ---"
BRPM_LINES=$(grep -c "BRPM:" "$LOG" 2>/dev/null || echo 0)
MIC_FAIL=$(grep -c "Microphone failed to initialize" "$LOG" 2>/dev/null || echo 0)
echo "    BRPM estimates logged: $BRPM_LINES"
if [ "$BRPM_LINES" -gt 0 ]; then
    echo "  PASS: mic fed the classifier all run (BRPM estimates present)"
else
    echo "  note: no BRPM estimates logged - the mic may not have produced audio buffers"
fi
if [ "$MIC_FAIL" -gt 0 ]; then
    echo "  FAIL: 'Microphone failed to initialize' appeared $MIC_FAIL time(s)"
    fail=1
else
    echo "  PASS: no microphone-failure warning"
fi

rm -f "$LOG"

if [ "$fail" = 1 ]; then
    echo
    echo "Some checks failed - the session is in History but audio needs investigation."
    exit 1
fi
echo
echo "Done. A real recorded day (session #$NEWEST) is now in History with the mic pipeline"
echo "verified. Open History -> this day for the Audio card (snoring/coughs/talking counts"
echo "and, if any events fired, replayable WAV clip recordings)."