#!/usr/bin/env python3
"""Full-app LeakCanary checkup for Somn (debug builds only).

Drives the whole app on a connected device -- seed data, finish onboarding,
visit all 5 tabs + detail screens, rotate, background/foreground, cold restart --
then collects every LeakCanary log line and writes a verdict report.

LeakCanary itself auto-watches destroyed Activities/Fragments/ViewModels/
Services, heap-dumps anything still retained 5s after destroy, and analyzes
on-device. This script is the legs; LeakCanary is the eyes.

Usage:  python3 scripts/leak_checkup.py [--device SERIAL] [--steps N]
Report: /tmp/opencode/leak-checkup/REPORT.md
Deps:   adb, python3 stdlib only.
"""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "dev.vic41148.somn"
SEED_ACTION = "dev.vic41148.somn.DEBUG_SEED_DATA"
OUT_DIR = "/tmp/opencode/leak-checkup"

DEVICE = None
MAX_TAPS = 120

NEXT_WORDS = ("next", "continue", "get started", "done", "skip",
              "got it", "ok", "allow", "enable", "finish", "start")
DOCK_TABS = ("Home", "Habits", "History", "Alarms", "Settings")


def adb(*args, check=False, timeout=60):
    cmd = ["adb"]
    if DEVICE:
        cmd += ["-s", DEVICE]
    cmd += list(args)
    return subprocess.run(cmd, capture_output=True, text=True,
                          timeout=timeout, check=check)


def shell(cmd, **kw):
    # Short timeout: a hung on-device call must stall one step, never the whole run.
    kw.setdefault("timeout", 25)
    try:
        return adb("shell", cmd, **kw)
    except subprocess.TimeoutExpired:
        print(f"TIMEOUT (continuing): {cmd}", flush=True)
        return subprocess.CompletedProcess(cmd, 124, "", "timeout")


def tap(x, y):
    shell(f"input tap {int(x)} {int(y)}")


def back():
    shell("input keyevent KEYCODE_BACK")
    time.sleep(1.5)


def dump_nodes(tries=3):
    """Return (nodes, packages). Retries the dump; a mid-transition screen is
    empty and must not be mistaken for 'nothing to tap'."""
    for _ in range(tries):
        r = shell("uiautomator dump /sdcard/lc_ui.xml")
        if r.returncode != 0 or "dumped to" not in (r.stdout + r.stderr):
            time.sleep(2)
            continue
        p = subprocess.run(
            (["adb"] + (["-s", DEVICE] if DEVICE else []) +
             ["pull", "/sdcard/lc_ui.xml", f"{OUT_DIR}/ui.xml"]),
            capture_output=True, text=True, timeout=60)
        if p.returncode != 0:
            time.sleep(2)
            continue
        try:
            root = ET.parse(f"{OUT_DIR}/ui.xml").getroot()
        except ET.ParseError:
            time.sleep(2)
            continue
        nodes, pkgs = [], set()
        for n in root.iter("node"):
            if n.get("package"):
                pkgs.add(n.get("package"))
            b = n.get("bounds", "")
            m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b)
            if not m:
                continue
            x1, y1, x2, y2 = map(int, m.groups())
            area = (x2 - x1) * (y2 - y1)
            text = n.get("text", "") or ""
            desc = n.get("content-desc", "") or ""
            # Full-screen containers are layout, never tap targets.
            if area > 1500000 and not text and not desc:
                continue
            nodes.append((text, desc, n.get("clickable") == "true",
                          (x1 + x2) / 2, (y1 + y2) / 2))
        if nodes:
            return nodes, pkgs
        time.sleep(2)
    return [], set()


def launch_app(note=None):
    shell(f"monkey -p {PKG} -c android.intent.category.LAUNCHER 1")
    time.sleep(4)


def wait_for_app(note, tries=8):
    """Ensure Somn is foreground; relaunch if we wandered into launcher/Leaks."""
    for i in range(tries):
        nodes, pkgs = dump_nodes()
        if PKG in pkgs:
            return nodes
        if any("leakcanary" in p for p in pkgs):
            m = re.search(r"(\d+)\s+distinct leaks", screen_text(nodes), re.I)
            if note and m:
                note(f"Leaks app open, showing {m.group(0)}")
            back()
            time.sleep(2)
            continue
        launch_app()
    return dump_nodes()[0]


def screen_text(nodes):
    return " ".join(t for t, d, _, _, _ in nodes for t in (t, d)).lower()


def tap_matching(nodes, words, skip_dock=True, require_clickable=True):
    """Tap first node matching any word. Preference order: labeled clickables,
    then labeled non-clickable centers (Compose splits text and tap handling
    across nodes), then any small clickable. Full-screen roots never qualify."""
    labeled_fallback, blind_fallback = None, None
    for text, desc, clickable, cx, cy in nodes:
        hay = f"{text} {desc}".lower()
        if skip_dock and hay.strip() in ("home", "habits", "history", "alarms", "settings"):
            continue
        if not any(w in hay for w in words):
            continue
        if clickable and (text or desc):
            tap(cx, cy)
            time.sleep(2)
            return text or desc
        if (text or desc) and labeled_fallback is None:
            labeled_fallback = (cx, cy, text or desc)
        elif clickable and blind_fallback is None:
            blind_fallback = (cx, cy, text or desc or "unlabeled")
    if labeled_fallback and not require_clickable:
        tap(labeled_fallback[0], labeled_fallback[1])
        time.sleep(2)
        return labeled_fallback[2]
    if blind_fallback and not require_clickable:
        tap(blind_fallback[0], blind_fallback[1])
        time.sleep(2)
        return blind_fallback[2]
    return None


def dismiss_dialogs(log, note):
    """Tap through system permission dialogs (ALLOW/Deny) so the crawl stays in-app."""
    for _ in range(6):
        nodes, _ = dump_nodes()
        texts = screen_text(nodes)
        if "allow" not in texts and "deny" not in texts and "while using" not in texts:
            return
        hit = tap_matching(nodes, ("allow", "while using",), skip_dock=False)
        if hit is None:
            hit = tap_matching(nodes, ("deny",), skip_dock=False)
        note(f"dialog dismissed via {hit!r}")
        time.sleep(1.5)


def on_home(nodes):
    hay = screen_text(nodes)
    return sum(1 for t in DOCK_TABS if t.lower() in hay) >= 4


def grant_permissions(log):
    out = shell("dumpsys package " + PKG, timeout=60).stdout
    granted = 0
    for m in set(re.findall(r"android\.permission\.[\w.]+", out)):
        if any(k in m for k in ("INTERNET", "FOREGROUND_SERVICE", "WAKE_LOCK",
                                "VIBRATE", "RECEIVE_BOOT_COMPLETED", "SCHEDULE_EXACT_ALARM",
                                "USE_FULL_SCREEN_INTENT", "REQUEST_IGNORE_BATTERY")):
            continue
        r = shell(f"pm grant {PKG} {m}")
        if r.returncode == 0:
            granted += 1
    log.append(f"granted ~{granted} runtime permissions via pm grant")


def main():
    global DEVICE, MAX_TAPS
    args = sys.argv[1:]
    if "--device" in args:
        DEVICE = args[args.index("--device") + 1]
    if "--steps" in args:
        MAX_TAPS = int(args[args.index("--steps") + 1])

    import os
    os.makedirs(OUT_DIR, exist_ok=True)
    log = []
    taps = 0

    def note(s):
        log.append(s)
        print(s, flush=True)

    note("clearing logcat, launching app")
    adb("logcat", "-c")
    launch_app()
    wait_for_app(note)

    note("seeding debug data via broadcast")
    shell(f"am broadcast -a {SEED_ACTION} -p {PKG}")
    time.sleep(12)

    grant_permissions(log)

    # --- onboarding sweep ---
    misses = 0
    for i in range(20):
        dismiss_dialogs(log, note)
        nodes = wait_for_app(note)
        if on_home(nodes):
            note("home dock visible, onboarding done/skipped")
            break
        hit = tap_matching(nodes, NEXT_WORDS, skip_dock=False,
                           require_clickable=False)
        note(f"onboarding tap {i}: {hit!r}")
        if hit is None:
            misses += 1
            time.sleep(3)
            if misses >= 4:
                note("WARN: no tappable onboarding controls, continuing anyway")
                break
            continue
        misses = 0

    # --- tab tour ---
    for tab in DOCK_TABS:
        for attempt in range(3):
            dismiss_dialogs(log, note)
            nodes = wait_for_app(note)
            hit = tap_matching(nodes, (tab.lower(),), skip_dock=False,
                               require_clickable=False)
            if hit is not None:
                break
            time.sleep(3)
        note(f"tab {tab}: tapped {hit!r}")
        taps += 1
        time.sleep(2)
        for dy in (800, -800):
            shell(f"input swipe 500 1200 500 {1200 + dy} 400")
            time.sleep(1)

    # --- detail crawl: tap clickables, back out, dedupe by screen signature ---
    seen = set()
    while taps < MAX_TAPS:
        dismiss_dialogs(log, note)
        nodes, pkgs = dump_nodes()
        if PKG not in pkgs:
            nodes = wait_for_app(note)
            continue
        sig = "|".join(sorted(f"{t}/{d}" for t, d, c, _, _ in nodes if c)[:20])
        if sig in seen:
            back()
            nodes, _ = dump_nodes()
            sig = "|".join(sorted(f"{t}/{d}" for t, d, c, _, _ in nodes if c)[:20])
            if sig in seen:
                break
        seen.add(sig)
        hit = tap_matching(nodes, ("",), skip_dock=True, require_clickable=False)
        if hit is None:
            break
        taps += 1
        note(f"crawl tap {taps}: {hit!r}")
        time.sleep(2)
        nodes2, _ = dump_nodes()
        if not on_home(nodes2):
            back()

    # --- lifecycle stress: rotate x3, background, cold restart ---
    note("lifecycle stress: rotation x3")
    shell("settings put system accelerometer_rotation 0")
    for i in range(3):
        shell(f"settings put system user_rotation {i % 2}")
        time.sleep(9)
    note("background 10s, foreground, force-stop, cold start")
    shell("input keyevent KEYCODE_HOME")
    time.sleep(10)
    launch_app()
    time.sleep(2)
    shell(f"am force-stop {PKG}")
    time.sleep(4)
    launch_app()
    wait_for_app(note)

    # --- collect ---
    note("waiting 75s for heap analysis, then collecting")
    time.sleep(75)
    lc = adb("logcat", "-d", "-s", "LeakCanary", timeout=60).stdout
    lines = [l for l in lc.splitlines() if "LeakCanary" in l]
    with open(f"{OUT_DIR}/leakcanary.log", "w") as f:
        f.write("\n".join(lines))

    watched = [l for l in lines if "Watching instance" in l]
    retained = [l for l in lines if re.search(r"retained|Retained", l)]
    dumps = [l for l in lines if re.search(r"dump|Dump|hprof", l)]
    found = [l for l in lines if re.search(r"leak found|Leak found|LEAK|1 leak|leaks? detected", l, re.I)
             and "ready to detect" not in l and "Watching" not in l]
    noleak = [l for l in lines if re.search(r"no leak|No leak|no retained", l, re.I)]

    report = [
        "# LeakCanary full checkup -- Somn",
        f"_Date: {time.strftime('%Y-%m-%d %H:%M %Z')} | device: "
        f"{adb('get-serialno').stdout.strip()} | build: debug (leakcanary 2.14)_",
        "",
        "## Coverage",
        f"- crawl taps: {taps} (+ tab tour, onboarding sweep, rotation x3, background, cold restart)",
        f"- instances watched (destroyed): {len(watched)}",
        "",
        "## Signals",
        f"- retained-after-destroy notes: {len(retained)}",
        f"- heap dumps: {len(dumps)}",
        f"- leak-found lines: {len(found)}",
        f"- no-leak confirmations: {len(noleak)}",
        "",
        "## Verdict",
        ("**LEAKS FOUND -- see excerpts below**" if (found or dumps)
         else "**LEAK-FREE** -- everything destroyed was GC'd, no heap dump triggered."),
        "",
    ]
    interesting = retained + dumps + found + noleak
    if interesting:
        report += ["## Excerpts", "```"] + interesting[:60] + ["```"]
    report += ["", "## Run log", "```"] + log + ["```",
               "", f"Full log: {OUT_DIR}/leakcanary.log ({len(lines)} lines)"]
    with open(f"{OUT_DIR}/REPORT.md", "w") as f:
        f.write("\n".join(report))
    print("\n".join(report), flush=True)


if __name__ == "__main__":
    main()
