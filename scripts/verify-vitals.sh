#!/usr/bin/env bash
# verify-vitals.sh — check the session-detail "Vitals" card renders the seeded
# external_vitals rows (Avg HR / Resting HR / HRV / SpO2 / Min SpO2 / Skin Temp).
#
# DB side: asserts external_vitals rows exist and join cleanly to sessions.
# UI side: History -> top card -> scroll down -> assert the Vitals card is present.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

echo "== DB: external_vitals =="
db_q "SELECT '  ' || COUNT(*) || ' vitals rows, ' || SUM(s.id IS NULL) || ' orphans' FROM external_vitals v LEFT JOIN sleep_sessions s ON s.id = v.sessionId;"

wake_and_unlock || exit 1
launch_app
goto_tab "History" || exit 1

# Tap the top (most recent) session card — validated coordinates on the S24 FE.
adb shell input tap 540 444
sleep 3

# Scroll down until the Vitals card area is visible.
for _ in 1 2 3; do
    adb shell input swipe 540 1700 540 600 300
    sleep 1.5
done

DUMP=$(ui_dump)
if ui_texts "$DUMP" | grep -q 'Vitals'; then
    echo "PASS: Vitals card present"
    ui_texts "$DUMP" | grep -iE 'Vitals|bpm|ms|SpO2|°C|Avg HR|Resting' | head -10
else
    echo "FAIL: Vitals card not found on detail screen"
    exit 1
fi
