#!/usr/bin/env bash
# reset-demo.sh [default|cycling] — one command to a pristine, verified demo state.
#   wipe -> re-seed -> sanity-check -> (any leftovers cleaned) -> summary.
#
# Usage:
#   reset-demo.sh            # baseline MALE demo
#   reset-demo.sh cycling    # FEMALE + CYCLING demo (today = LUTEAL)
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

PROFILE="${1:-default}"
case "$PROFILE" in
  default|male) PROFILE="default" ;;
  cycling|female) PROFILE="cycling" ;;
  *) echo "usage: reset-demo.sh [default|cycling]" >&2; exit 1 ;;
esac

"$(dirname "${BASH_SOURCE[0]}")/seed-demo.sh" "$PROFILE"
# A fresh seed has no junk, but this also catches any half-aborted earlier runs.
"$(dirname "${BASH_SOURCE[0]}")/cleanup-junk-sessions.sh" --yes >/dev/null 2>&1 || true

echo
echo "== final demo state =="
db_q "SELECT '  profile:  ' || biologicalSex || '/' || lifeStage || '  lastPeriod=' || COALESCE(lastPeriodStartDate,'<none>') FROM user_profile WHERE id=1;"
db_q "SELECT '  sessions: ' || COUNT(*) || '  epochs: ' || (SELECT COUNT(*) FROM sleep_epochs) || '  vitals: ' || (SELECT COUNT(*) FROM external_vitals) || '  alarms: ' || (SELECT COUNT(*) FROM alarms);"
"$(dirname "${BASH_SOURCE[0]}")/verify-db-phase.sh"
echo "Reset complete ($PROFILE)."
