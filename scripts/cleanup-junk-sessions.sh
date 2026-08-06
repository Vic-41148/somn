#!/usr/bin/env bash
# cleanup-junk-sessions.sh [--keep N] [--yes] — delete sessions created after the
# seeded demo nights (smoke-test junk: 1-minute score-30 sessions) and push the DB
# back, leaving the 7 seeded nights pristine.
#
# Default keeps the first 7 sessions (the seeder's nights) and deletes everything
# with a higher id, children first. Lists what it will delete and asks for
# confirmation unless --yes.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

KEEP=7
YES=0
[ "${1:-}" = "--keep" ] && { KEEP="${2:-7}"; shift 2; }
[ "${1:-}" = "--yes" ] && YES=1

HOST=$(db_pull) || exit 1
trap 'rm -f "$HOST" "$HOST-wal" "$HOST-shm"' EXIT

COUNT=$(sqlite3 "$HOST" "SELECT COUNT(*) FROM sleep_sessions WHERE id > $KEEP;")
if [ "$COUNT" = "0" ]; then
    echo "Nothing to clean — $KEEP seeded sessions and no extras."
    exit 0
fi

echo "Sessions that will be DELETED (id > $KEEP):"
sqlite3 -header -column "$HOST" \
    "SELECT id, datetime(startTimeMillis/1000,'unixepoch','localtime') AS start, sleepScore, sleepDurationMinutes AS mins, isCompleted FROM sleep_sessions WHERE id > $KEEP;"

if [ "$YES" != "1" ]; then
    read -r -p "Delete these $COUNT session(s)? [y/N] " ans
    case "$ans" in y|Y) ;; *) echo "Aborted."; exit 0 ;; esac
fi

# Stop the app so Room isn't mid-write while we swap the DB.
adb shell am force-stop "$PKG"

sqlite3 "$HOST" "DELETE FROM sleep_epochs   WHERE sessionId > $KEEP;"
sqlite3 "$HOST" "DELETE FROM audio_events   WHERE sessionId > $KEEP;"
sqlite3 "$HOST" "DELETE FROM external_vitals WHERE sessionId > $KEEP;"
sqlite3 "$HOST" "DELETE FROM session_tags   WHERE sessionId > $KEEP;"
sqlite3 "$HOST" "DELETE FROM sleep_sessions WHERE id > $KEEP;"
sqlite3 "$HOST" 'PRAGMA wal_checkpoint(TRUNCATE);' >/dev/null 2>&1

db_push "$HOST"

echo "Cleaned. Final state:"
db_q "SELECT '  ' || COUNT(*) || ' sessions, ' || (SELECT COUNT(*) FROM sleep_epochs) || ' epochs, ' || (SELECT COUNT(*) FROM external_vitals) || ' vitals';"
