#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# seed-somn-demo.sh — reproducibly seed the Somn DEBUG build with FABRICATED
# demo state on a connected device (exactly one adb device).
#
#   Wipes any existing Somn install/data on the device (uninstall), installs the
#   latest debug APK, then seeds:
#     • a fabricated profile (birth date 1994-05-17, MALE placeholder, rMEQ 18)
#     • 7 fabricated sleep sessions — the last 7 nights, varied scores 45-88,
#       with ~6.7k sleep epochs, 15 audio events and ~34 habit logs
#   by editing the Room SQLite DB directly via `run-as` (debug build = debuggable).
#
#   No real device data is ever read or recorded — this is fabricate-from-scratch.
#
#   CI-ignored guard: refuses to run when CI=true (GitHub Actions exports it)
#   and refuses to run without exactly one connected device, so this destructive
#   tool can never trip a pipeline or hang a headless runner.
#   Usage:   bash scripts/seed-somn-demo.sh [--yes] [--profile cycling|default]
#   --yes            skip the destructive-uninstall confirmation (for scripted runs)
#   --profile cycling  seed a FEMALE + CYCLING profile (exercises the cycle UI)
#   --profile default  seed the baseline MALE profile (default)
# Deps:    adb, a connected authorized device, gradle (for the APK build),
#          host `sqlite3` + `python3` (with sqlite3 stdlib).
# Idempotent: safe to re-run at any time; re-seeds to the same state.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

PKG="dev.vic41148.somn"
DB_NAME="sleep_tracker.db"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"          # scripts/
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"                        # repo root
SEED_PY="$SCRIPT_DIR/seed-somn-demo.py"
WORK="$(mktemp -d /tmp/somnseed.XXXXXX)"
trap 'rm -rf "$WORK"' EXIT

# ── CI-ignored guard ────────────────────────────────────────────────────────
CI_FLAG="${CI:-}"
YES=""
PROFILE=""
ARGS=("$@")
for ((i = 0; i < ${#ARGS[@]}; i++)); do
  case "${ARGS[i]}" in
    --yes) YES=1 ;;
    --profile) PROFILE="${ARGS[i + 1]:-}"; i=$((i + 1)) ;;
  esac
done
case "$PROFILE" in
  cycling|female-cycling) PROFILE="female-cycling" ;;
  default|male-default|"") PROFILE="male-default" ;;
  *) echo "Unknown --profile '$PROFILE' (use: cycling|default)" >&2; exit 1 ;;
esac

if [ "$CI_FLAG" = "true" ]; then
  echo "Refusing to run in CI: this seeder uninstalls the app on a real device and" >&2
  echo "seeds fabricated data. Run it locally against a development device instead." >&2
  exit 1
fi

DEVICE_COUNT=$(adb devices | awk 'NR > 1 && $2 == "device" { n++ } END { print n + 0 }')
if [ "$DEVICE_COUNT" -ne 1 ]; then
  echo "Expected exactly one connected device, found $DEVICE_COUNT." >&2
  echo "Connect a device (authorized) and re-run." >&2
  exit 1
fi
SERIAL="$(adb get-serialno)"
# Timezone comes from the device so timestamps are right on any device — the
# python seeder reads it via SOMN_SEED_TZ instead of a hardcoded zone.
export SOMN_SEED_TZ="$(adb shell getprop persist.sys.timezone 2>/dev/null | tr -d '\r')"
export SOMN_SEED_PROFILE="$PROFILE"

if [ -z "$YES" ]; then
  if ! read -r -p "This wipes any existing Somn install + data on $SERIAL and seeds FABRICATED demo data. Continue? [y/N] " answer; then
    echo "Aborted."
    exit 0
  fi
  case "$answer" in
    y|Y) ;;
    *) echo "Aborted."; exit 0 ;;
  esac
fi

echo "== [1/9] build latest debug APK"
(cd "$PROJECT_ROOT" && ./gradlew assembleDebug --console=plain -q)
APK="$PROJECT_ROOT/app/build/outputs/apk/debug/app-debug.apk"
[ -f "$APK" ] || { echo "APK not found: $APK"; exit 1; }

echo "== [2/9] uninstall any existing Somn (data intentionally wiped — fabricated state only)"
adb uninstall "$PKG" >/dev/null 2>&1 || true
adb uninstall "$PKG.core.audio.test" >/dev/null 2>&1 || true

echo "== [3/9] install debug APK"
adb install -r "$APK"

echo "== [4/9] grant runtime permissions (demo convenience)"
for p in RECORD_AUDIO BODY_SENSORS POST_NOTIFICATIONS; do
  adb shell pm grant "$PKG" "android.permission.$p" >/dev/null 2>&1 || echo "  (grant $p skipped)"
done

echo "== [5/9] first launch — creates the Room DB (onboarding gate: user_profile.onboardingCompleted)"
adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 8
adb shell am force-stop "$PKG"
sleep 1

echo "== [6/9] pull the Room DB (+ WAL) via run-as"
cd "$WORK"
adb exec-out run-as "$PKG" cat "databases/$DB_NAME" > "$DB_NAME"
adb exec-out run-as "$PKG" cat "databases/$DB_NAME-wal" > "$DB_NAME-wal" 2>/dev/null || true
adb exec-out run-as "$PKG" cat "databases/$DB_NAME-shm" > "$DB_NAME-shm" 2>/dev/null || true
sqlite3 "$DB_NAME" 'PRAGMA wal_checkpoint(TRUNCATE);' >/dev/null 2>&1 || true

echo "== [7/9] seed fabricated $PROFILE profile + sessions + epochs + audio + habits (host sqlite3 via python)"
python3 "$SEED_PY"

echo "== [8/9] push DB back and relaunch"
adb push "$DB_NAME" /data/local/tmp/"$DB_NAME" >/dev/null
adb shell run-as "$PKG" cp /data/local/tmp/"$DB_NAME" "databases/$DB_NAME"
adb shell run-as "$PKG" rm -f "databases/$DB_NAME-wal" "databases/$DB_NAME-shm"
adb shell rm -f /data/local/tmp/"$DB_NAME"
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 8

echo "== [9/9] verify — Home should show Last Night with the seeded session"
adb shell uiautomator dump /sdcard/seed_verify.xml >/dev/null 2>&1
adb shell cat /sdcard/seed_verify.xml | grep -oE 'text="[^"]{1,50}"' | grep -v 'text=""' | head -12
adb shell rm -f /sdcard/seed_verify.xml

echo
echo "Seeded ($PROFILE). Check: Home 'Last Night' (7h 30m, score 84), History = 7 cards"
echo "(94/88/95/79/91/76/85% eff), detail screen of Thu shows 88 Great 9h15m,"
echo "audio events card populated, Habits Today's Log has entries."
if [ "$PROFILE" = "female-cycling" ]; then
  echo "Cycle variant: Trends shows Follicular/Ovulation/Luteal bands; today is LUTEAL."
fi
