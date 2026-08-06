#!/usr/bin/env bash
# seed-demo.sh [default|cycling] — wipe + re-seed the demo state.
#
# Thin wrapper around the tracked scripts/seed-somn-demo.sh (which uninstalls,
# rebuilds the debug APK, installs, seeds the Room DB and relaunches the app).
# Adds a post-seed sanity check (profile row + session count).
#
# Usage:
#   seed-demo.sh            # baseline MALE profile
#   seed-demo.sh cycling    # FEMALE + CYCLING (last period = today-17 -> today is LUTEAL)
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

PROFILE="${1:-default}"
case "$PROFILE" in
  default|male)            PROFILE="default"  ;;
  cycling|female|fem)      PROFILE="cycling"  ;;
  *) echo "usage: seed-demo.sh [default|cycling]" >&2; exit 1 ;;
esac

bash "$(dirname "${BASH_SOURCE[0]}")/seed-somn-demo.sh" --yes --profile "$PROFILE"

echo
echo "== post-seed sanity =="
launch_app
sleep 2
echo "--- profile ---"
db_q "SELECT '  ' || biologicalSex || '/' || lifeStage || '  lastPeriod=' || COALESCE(lastPeriodStartDate,'<none>') || '  chronotype=' || chronotype FROM user_profile WHERE id=1;"
echo "--- sessions ---"
db_q "SELECT '  ' || COUNT(*) || ' sessions, scores: ' || GROUP_CONCAT(sleepScore, ',') FROM sleep_sessions;"
echo "Seeded ($PROFILE)."
