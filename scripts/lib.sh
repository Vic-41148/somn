#!/usr/bin/env bash
# lib.sh — shared helpers for the Somn dev/test scripts in this folder.
# Every script here sources it:  source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
#
# Covers everything the ad-hoc one-off scripts kept re-implementing:
#   - waking/unlocking the device (and failing loudly on a secure lock screen)
#   - launching the app fresh (force-stop -> relaunch so Room reads the seeded DB)
#   - finding UI nodes in uiautomator dumps and tapping their centers
#   - pulling/pushing the Room DB via run-as (with WAL checkpoint)
#   - asserting on posted notifications via dumpsys
#   - live logcat capture to a host file (ring buffer rotates too fast for post-hoc -d)
#
# All helpers are set -e safe: "not found" cases echo "" / return 1 rather than aborting.

export PATH="$PATH:${ANDROID_HOME:-$ANDROID_SDK_ROOT}/platform-tools"

PKG="dev.vic41148.somn"
DB_NAME="sleep_tracker.db"
DB_HOST_DIR="/tmp/somnseed"
# Repo root — provided utility for any script that needs to reach app sources/builds.
# Nearest ancestor containing settings.gradle.kts (walk-up, so it works from scripts/ or
# wherever the scripts are invoked from). Fails loudly instead of silently degrading to "/"
# if the marker is never found.
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && while [ ! -f settings.gradle.kts ] && [ "$PWD" != "/" ]; do cd ..; done && pwd)"
if [ ! -f "$REPO_ROOT/settings.gradle.kts" ]; then
    echo "ERROR: could not locate the repo root (settings.gradle.kts) above $(dirname "${BASH_SOURCE[0]}")" >&2
    exit 1
fi

# ── screen / keyguard ─────────────────────────────────────────────────────────

screen_is_locked() {
    adb shell dumpsys window 2>/dev/null | grep -qiE \
        'mDreamingLockscreen=true|mShowingLockscreen=true|mKeyguardShowing=true'
}

# Wakes the device, dismisses a swipe keyguard and pins the screen on while charging.
# Returns 1 with a clear message if a SECURE lock (PIN/password/face) is up — adb
# cannot bypass that, so the human has to unlock the phone.
wake_and_unlock() {
    adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
    adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
    adb shell svc power stayon true >/dev/null 2>&1 || true
    sleep 1
    if screen_is_locked; then
        echo "ERROR: the device is on a secure lock screen and adb can't bypass it." >&2
        echo "       Unlock the phone (face/PIN/swipe) then re-run." >&2
        return 1
    fi
}

# Fresh process: force-stop then relaunch, so Room reads whatever DB is on disk.
launch_app() {
    adb shell am force-stop "$PKG" 2>/dev/null || true
    sleep 1
    adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
    sleep 9
    adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
    sleep 1
}

# ── uiautomator helpers ────────────────────────────────────────────────────────

# Dumps the UI hierarchy to /sdcard and echoes the device path.
ui_dump() {
    local f="/sdcard/uidump_$$.xml"
    adb shell uiautomator dump "$f" >/dev/null 2>&1 || true
    echo "$f"
}

# All non-empty text nodes in a dump, one per line.
ui_texts() {
    adb shell cat "$1" 2>/dev/null | grep -oE 'text="[^"]+"' | grep -v 'text=""' || true
}

# Center "X Y" of the first node whose attributes match $1 (a regex fragment, e.g.
# 'text="History"' or 'content-desc="Start sleep tracking"') in dump $2. Echoes ""
# when absent. Example:  c=$(find_center 'text="History"' "$dump")
find_center() {
    local match="$1" dump="$2" bounds=""
    bounds=$(adb shell cat "$dump" 2>/dev/null \
        | grep -oE "<node[^>]*${match}[^>]*bounds=\"\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]\"" \
        | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' \
        | head -1 || true)
    [ -z "$bounds" ] && { echo ""; return 0; }
    local x1 y1 x2 y2
    x1=$(echo "$bounds" | grep -oE '[0-9]+' | sed -n 1p)
    y1=$(echo "$bounds" | grep -oE '[0-9]+' | sed -n 2p)
    x2=$(echo "$bounds" | grep -oE '[0-9]+' | sed -n 3p)
    y2=$(echo "$bounds" | grep -oE '[0-9]+' | sed -n 4p)
    echo "$(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))"
}

tap_at() { adb shell input tap $1; sleep 1; }

# ── navigation shortcuts (validated on the Galaxy S24 FE) ─────────────────────

goto_tab() {  # $1 = tab text: History | Alarms | ...
    local f c
    f=$(ui_dump)
    c=$(find_center "text=\"$1\"" "$f")
    [ -z "$c" ] && { echo "ERROR: tab '$1' not found on screen" >&2; return 1; }
    tap_at "$c"
    sleep 3
}

# Home screen moon button — starts a real tracking session (navigates to Tracking).
tap_moon() {
    local f c
    f=$(ui_dump)
    c=$(find_center 'content-desc="Start sleep tracking"' "$f")
    [ -z "$c" ] && { echo "ERROR: moon button (Start sleep tracking) not found — is Home visible?" >&2; return 1; }
    tap_at "$c"
    sleep 4
}

# Tracking screen stop button.
tap_wake_up() {
    local f c
    f=$(ui_dump)
    c=$(find_center 'text="[^"]*Wake Up[^"]*"' "$f")
    [ -z "$c" ] && { echo "ERROR: Wake Up button not found on Tracking screen" >&2; return 1; }
    tap_at "$c"
}

# History -> scroll to the bottom -> View Trends.
goto_trends() {
    goto_tab "History"
    for _ in 1 2 3 4; do
        adb shell input swipe 540 1700 540 500 300
        sleep 1.2
    done
    local f c
    f=$(ui_dump)
    c=$(find_center 'text="View Trends"' "$f")
    [ -z "$c" ] && { echo "ERROR: View Trends not found after scrolling History" >&2; return 1; }
    tap_at "$c"
    sleep 4
}

# ── Room DB via run-as ─────────────────────────────────────────────────────────

# Pulls a checkpointed copy of the DB to the host; echoes the host path ("" on failure).
# The pull is verified (valid SQLite header + readable) and retried once — adb exec-out
# has occasionally returned a truncated file under load, which corrupts every check.
db_pull() {
    mkdir -p "$DB_HOST_DIR"
    local host="$DB_HOST_DIR/somn_$$.db"
    rm -f "$host" "$host-wal" "$host-shm"
    for _ in 1 2; do
        adb exec-out run-as "$PKG" cat "databases/$DB_NAME" > "$host" 2>/dev/null || true
        adb exec-out run-as "$PKG" cat "databases/$DB_NAME-wal" > "$host-wal" 2>/dev/null || true
        sqlite3 "$host" 'PRAGMA wal_checkpoint(TRUNCATE);' >/dev/null 2>&1 || true
        if [ -s "$host" ] && sqlite3 "$host" 'PRAGMA schema_version;' >/dev/null 2>&1; then
            echo "$host"; return 0
        fi
        echo "  (db_pull: retrying once — previous read looked truncated)" >&2
        sleep 1
    done
    echo ""
    return 1
}

# Pushes a host DB copy back over the app's DB (app must be force-stopped first).
db_push() {
    local host="$1"
    adb push "$host" /data/local/tmp/somn_push.db >/dev/null 2>&1 || return 1
    adb shell run-as "$PKG" cp /data/local/tmp/somn_push.db "databases/$DB_NAME"
    adb shell run-as "$PKG" rm -f "databases/$DB_NAME-wal" "databases/$DB_NAME-shm"
    adb shell rm -f /data/local/tmp/somn_push.db
}

# Runs $1 as a SQLite query against the device DB, prints the result, cleans up.
db_q() {
    local host
    host=$(db_pull) || { echo "DB pull failed" >&2; return 1; }
    sqlite3 "$host" "$1"
    rm -f "$host" "$host-wal" "$host-shm"
}

# ── notifications ──────────────────────────────────────────────────────────────

# 0/1: is the exact text present in the posted-notification dump?
notification_text_present() {
    adb shell dumpsys notification --noredact 2>/dev/null | grep -qF "$1"
}

# ── live logcat ────────────────────────────────────────────────────────────────

LOGCAT_PID=""
logcat_start() {  # $1 = host log file
    adb logcat > "$1" 2>&1 &
    LOGCAT_PID=$!
}
logcat_stop() {
    [ -n "$LOGCAT_PID" ] && kill "$LOGCAT_PID" 2>/dev/null || true
    LOGCAT_PID=""
    sleep 1
}
