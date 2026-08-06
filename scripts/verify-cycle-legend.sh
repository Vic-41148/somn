#!/usr/bin/env bash
# verify-cycle-legend.sh [male|cycling] — check the Trends screen cycle-phase legend.
#
# The Trends legend ("Cycle phase" + the 5 phase rows) only renders when the profile
# has cycle features on (FEMALE + CYCLING) — the UI gates on it via
# TrendsViewModel.cyclePhaseRuns (null otherwise). Pass the expected profile to assert.
#
# Usage:
#   verify-cycle-legend.sh cycling   # PASS if the legend renders
#   verify-cycle-legend.sh male      # PASS if it does NOT render
#   verify-cycle-legend.sh           # print whether it renders, no assertion
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

EXPECT="${1:-}"

wake_and_unlock || exit 1
launch_app
goto_trends || exit 1

DUMP=$(ui_dump)
if ui_texts "$DUMP" | grep -qiE 'Cycle phase|Menstrual|Follicular|Ovulation|Luteal|Premenstrual'; then
    PRESENT=1
    echo "cycle legend: PRESENT"
else
    PRESENT=0
    echo "cycle legend: absent"
fi

case "$EXPECT" in
    cycling) [ "$PRESENT" = "1" ] && { echo "PASS"; exit 0; } || { echo "FAIL: expected legend"; exit 1; } ;;
    male)    [ "$PRESENT" = "0" ] && { echo "PASS"; exit 0; } || { echo "FAIL: legend unexpectedly present"; exit 1; } ;;
    *) exit 0 ;;
esac
