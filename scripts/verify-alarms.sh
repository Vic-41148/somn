#!/usr/bin/env bash
# verify-alarms.sh — check the Alarms screen shows the two seeded alarms
# (enabled Workday 7:00 AM with smart wake + disabled Weekend lie-in 9:30 AM).
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

echo "== DB: alarms =="
db_q "SELECT '  ' || COUNT(*) || ' alarms: ' || GROUP_CONCAT(label, ' | ') FROM alarms;"

wake_and_unlock || exit 1
launch_app
goto_tab "Alarms" || exit 1
sleep 1

DUMP=$(ui_dump)
TEXTS=$(ui_texts "$DUMP")
echo "$TEXTS" | head -20

ok=1
echo "$TEXTS" | grep -q 'Workday'        || { echo "FAIL: Workday alarm missing"; ok=0; }
echo "$TEXTS" | grep -q 'Weekend lie-in' || { echo "FAIL: Weekend lie-in missing"; ok=0; }
echo "$TEXTS" | grep -qi 'Smart wake'    || { echo "FAIL: smart-wake caption missing"; ok=0; }
[ "$ok" = "1" ] && { echo "PASS: Alarms screen shows both seeded alarms"; } || exit 1
