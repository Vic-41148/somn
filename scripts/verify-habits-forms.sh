#!/usr/bin/env bash
# verify-habits-forms.sh — regression test for the DailyLogScreen habit forms.
#
# Guards against the compose foundation version drift that crashed every habit
# form with NoSuchMethodError on FlowRow the moment it expanded (habits Caffeine
# tap). Fixed by the 2025.08.01 BOM bump (whole stack aligned at 1.9.0); this
# script proves all four forms expand, render their content, and never crash.
#
# FlowRow sites under test:
#   - CaffeineLogForm  (line ~308) — CaffeineSource chips
#   - ExerciseLogForm  (line ~391) — ExerciseType chips
# The Alcohol and Stress forms don't use FlowRow but live in the same screen, so
# they're covered for completeness.
#
# Usage:
#   verify-habits-forms.sh
# Exit 0 = PASS, 1 = FAIL.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

LOG=$(mktemp /tmp/habits_forms_XXXXXX.log)
logcat_start "$LOG"
trap 'logcat_stop' EXIT

wake_and_unlock || exit 1
launch_app
goto_tab "Habits" || exit 1

# Sanity: we are on the Daily Log screen.
DUMP=$(ui_dump)
if ! ui_texts "$DUMP" | grep -qF "Today's Log"; then
    echo "FAIL: Habits tab did not land on the Daily Log screen"
    exit 1
fi
echo "OK: Daily Log screen reached"

FAIL=0

# Tap the section header, assert the app survives and the expected content renders,
# then tap the header again to collapse (keeps the next section on-screen).
tap_and_assert() {
    local label="$1" pattern="$2" note="$3"
    local f c
    f=$(ui_dump)
    c=$(find_center "text=\"$label\"" "$f")
    if [ -z "$c" ]; then
        echo "FAIL: '$label' header not found on the Daily Log screen"
        FAIL=1
        return
    fi
    tap_at "$c"
    sleep 3
    if ! adb shell pidof "$PKG" >/dev/null 2>&1; then
        echo "FAIL: app died after tapping '$label' — form expansion crashed (the FlowRow regression?)"
        FAIL=1
        return
    fi
    f=$(ui_dump)
    if ! ui_texts "$f" | grep -qF "$pattern"; then
        echo "FAIL: tapping '$label' did not render '$pattern' ($note)"
        FAIL=1
        return
    fi
    echo "OK: '$label' form expanded, '$pattern' visible ($note)"
    # Collapse so the next header stays on-screen. (A missed collapse pushes the
    # remaining sections below the fold — see the cascade note in the FAIL summary.)
    c=$(find_center "text=\"$label\"" "$f")
    if [ -n "$c" ]; then tap_at "$c"; fi
    sleep 2
}    # Assertion patterns must stay unique against the "Logged today" rows below the
    # sections (the seed logs "Coffee — 95mg at 08:30" etc.) — a logged entry matching
    # the pattern would false-positive even if the form never expanded. Keep patterns to
    # text that only exists inside the expanded form.
    #
    # Caffeine — FlowRow #1 (the original crash). Assert a unique chip text renders.
    tap_and_assert "Caffeine" "Energy drink (160mg)" "FlowRow #1 chips render"

    # Alcohol — plain form, no FlowRow.
    tap_and_assert "Alcohol" "Units: 1.0" "slider + units label render"

    # Exercise — FlowRow #2. Assert a unique type chip renders.
    tap_and_assert "Exercise" "Strength training" "FlowRow #2 chips render"

    # Stress — plain form.
    tap_and_assert "Stress" "Log stress level 3" "level selector + button render"

logcat_stop
trap - EXIT

if grep -q 'FATAL EXCEPTION' "$LOG"; then
    echo "FAIL: FATAL exception in logcat during the form walk:"
    grep -A 8 'FATAL EXCEPTION' "$LOG" | head -12
    exit 1
fi

if ! adb shell pidof "$PKG" >/dev/null 2>&1; then
    echo "FAIL: app process is dead after the form walk"
    exit 1
fi

[ "$FAIL" -eq 0 ] && { echo "PASS: all four habit forms expand and render without crashing"; exit 0; }
# Note: on a failed assertion the section is NOT collapsed (the function returns before
# the collapse tap), so later sections can be pushed below the fold and report as
# "header not found" — those are cascades of the first failure, not separate bugs.
echo "FAIL: one or more habit forms failed (see above; later failures may be cascades)"
exit 1
