#!/usr/bin/env bash
# verify-db-phase.sh [--expect PHASE] — replicate the app's menstrual-cycle phase math
# against the on-device DB (mirrors MenstrualCyclePhase.currentPhase and
# TrendsViewModel.computeCyclePhaseRuns) and print where today falls.
#
# Usage:
#   verify-db-phase.sh                  # print profile + today's phase + band runs
#   verify-db-phase.sh --expect LUTEAL  # also assert today's phase (exit 1 on mismatch)
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

EXPECT=""
[ "${1:-}" = "--expect" ] && EXPECT="${2:-}"

HOST=$(db_pull) || exit 1
trap 'rm -f "$HOST" "$HOST-wal" "$HOST-shm"' EXIT

python3 - "$HOST" "$EXPECT" <<'PYEOF'
import sqlite3, sys
from datetime import datetime, timedelta

host, expect = sys.argv[1], sys.argv[2] or None
c = sqlite3.connect(host).cursor()

row = c.execute(
    "SELECT biologicalSex, lifeStage, lastPeriodStartDate, cycleLength FROM user_profile WHERE id=1"
).fetchone()
if not row or not row[2]:
    print("NO lastPeriodStartDate -> cycle features off, phase is null")
    sys.exit(1 if expect else 0)

sex, stage, lp_str, cl = row
lp = datetime.strptime(lp_str, "%Y-%m-%d").date()
today = datetime.now().date()
days = (today - lp).days
if days < 0:
    print("lastPeriodStart is in the future"); sys.exit(1)
cycle_day = (days % cl) + 1

def phase_at(day):
    d = (day - lp).days % cl + 1
    if d <= 5: return "MENSTRUAL"
    if d <= cl - 15: return "FOLLICULAR"
    if d == cl - 14: return "OVULATION"
    if d <= cl - 7: return "LUTEAL"
    return "PREMENSTRUAL"

print(f"profile: {sex}/{stage}  cycleLen={cl}  lastPeriod={lp_str}")
print(f"today:   {today}  daysSince={days}  cycleDay={cycle_day} -> {phase_at(today)}")

start = datetime.fromtimestamp(
    c.execute("SELECT MIN(startTimeMillis) FROM sleep_sessions").fetchone()[0] / 1000).date()
end = datetime.fromtimestamp(
    c.execute("SELECT MAX(startTimeMillis) FROM sleep_sessions").fetchone()[0] / 1000).date()
runs, cur, rs = [], None, None
day = start
while day <= end:
    p = phase_at(day)
    if p != cur:
        if cur is not None:
            runs.append((cur, rs, day - timedelta(days=1)))
        cur, rs = p, day
    day += timedelta(days=1)
if cur is not None:
    runs.append((cur, rs, end))
print("session-window bands (as Trends draws them):")
for phase, a, b in runs:
    print(f"  {phase:<12} {a} .. {b}   (cycle day {(a - lp).days + 1})")

if expect:
    got = phase_at(today)
    if got == expect:
        print(f"PASS: today is {expect}")
    else:
        print(f"FAIL: expected {expect}, got {got}")
        sys.exit(1)
PYEOF
