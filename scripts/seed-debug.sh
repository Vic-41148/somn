#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# seed-debug.sh - seed the Somn DEBUG build with a week of FABRICATED sleep data
# on a connected device, using the in-app DebugSeedReceiver (debug source set) rather
# than the host-sqlite3 run-as edit path of seed-somn-demo.sh.
#
# The receiver inserts data through the app's own Room repositories (SleepRepository /
# HabitLogRepository / TagRepository), so every row is internally consistent with the
# live mappers - no DB pull/edit/push, no host sqlite3/python, and no risk of
# corrupting the Room WAL. The receiver is compiled only into debug variants and is
# never present in a release build.
#
# Seeds: 7 MAIN_SLEEP nights (last 7 mornings) with epochs + external vitals + some
# audio events, a couple of habit logs (caffeine/alcohol/exercise/stress), and a
# "Weekend" tag on weekend sessions. Deterministic (fixed Random seed) so re-runs
# reproduce the same shape.
#
#   Usage:   bash scripts/seed-debug.sh [--yes]
#   --yes    skip the destructive-uninstall confirmation (for scripted runs)
# Deps:    adb, a connected authorized device, gradle (for the APK build).
# CI-ignored guard: refuses to run when CI=true and refuses without one device.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

PKG="dev.vic41148.somn"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"    # scripts/
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"                  # repo root

# ── CI-ignored guard ────────────────────────────────────────────────────────
CI_FLAG="${CI:-}"
YES=""
for arg in "$@"; do
  case "$arg" in --yes) YES=1 ;; esac
done
if [ "$CI_FLAG" = "true" ]; then
  echo "Refusing to run in CI: this seeder uninstalls the app on a real device and seeds" >&2
  echo "fabricated data. Run it locally against a development device instead." >&2
  exit 1
fi

# ── device guard (mirrors seed-somn-demo.sh) ────────────────────────────────
if [ -n "${ANDROID_SERIAL:-}" ]; then
  DEVICE_COUNT=$(adb devices | awk -v want="$ANDROID_SERIAL" \
    'NR > 1 && $2 == "device" && $1 == want { n++ } END { print n + 0 }')
else
  DEVICE_COUNT=$(adb devices | awk 'NR > 1 && $2 == "device" { n++ } END { print n + 0 }')
fi
if [ "$DEVICE_COUNT" -ne 1 ]; then
  echo "Expected exactly one connected device${ANDROID_SERIAL:+ (target: $ANDROID_SERIAL)}, found $DEVICE_COUNT." >&2
  exit 1
fi
SERIAL="$(adb get-serialno)"

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

echo "== [1/5] build latest debug APK"
(cd "$PROJECT_ROOT" && ./gradlew assembleStandaloneDebug --console=plain -q)
APK="$PROJECT_ROOT/app/build/outputs/apk/standalone/debug/app-standalone-debug.apk"
[ -f "$APK" ] || { echo "APK not found: $APK"; exit 1; }

echo "== [2/5] uninstall any existing Somn (fresh install - data intentionally wiped)"
adb uninstall "$PKG" >/dev/null 2>&1 || true

echo "== [3/5] install debug APK"
adb install -r "$APK"

echo "== [4/5] broadcast DEBUG_SEED_DATA to the in-app seeder"
adb shell am broadcast \
  -n "$PKG/dev.vic41148.somn.app.debug.DebugSeedReceiver" \
  -a "dev.vic41148.somn.DEBUG_SEED_DATA"
sleep 3

echo "== [5/5] relaunch app"
adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1

echo
echo "Seeded a week (7 nights + vitals + epochs + habits + a Weekend tag)."
echo "Check: Home 'Last Night', Trends, History (7 cards), Sleep Debt, Circadian."
