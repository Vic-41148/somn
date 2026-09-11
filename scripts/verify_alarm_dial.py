#!/usr/bin/env python3
"""Device check for the alarm time dial hand (the AM/PM-toggle regression).

Automates the manual verification that caught the "hand vanishes after toggling
AM/PM then dragging" bug:

  1. install and launch the standalone debug APK
  2. navigate to the New Alarm screen (Alarms tab -> Add alarm)
  3. tap PM
  4. screenshot and assert the hand is still drawn at 7 o'clock
  5. drag the hand to 12 o'clock
  6. screenshot and assert the hand is drawn at 12 o'clock

The hand color is the Material3 primary used on this dark theme. Requires a
device/emulator on adb and Python 3. Usage:

    python3 scripts/verify_alarm_dial.py [--apk PATH] [--serial SERIAL]

Exit code is 0 on pass, 1 on any failed assertion.
"""

import argparse
import math
import re
import struct
import subprocess
import sys
import tempfile
import time
import zlib

HAND_RGB = (176, 198, 255)
TOLERANCE = 12
RING_R0 = 40
RING_R1 = 270
MIN_LINE_PIXELS = 250

PACKAGE = "dev.vic41148.somn"


def sh(*args, timeout=180):
    return subprocess.run(args, capture_output=True, text=True, timeout=timeout)


def adb(serial, *args, timeout=180):
    cmd = ["adb"]
    if serial:
        cmd += ["-s", serial]
    cmd += list(args)
    result = sh(*cmd, timeout=timeout)
    if result.returncode != 0:
        raise SystemExit(f"adb {' '.join(args)} failed: {result.stderr.strip() or result.stdout.strip()}")
    return result.stdout


def tap(serial, x, y):
    adb(serial, "shell", "input", "tap", str(x), str(y))


def ui_dump(serial):
    return adb(serial, "exec-out", "uiautomator", "dump", "/dev/tty")


def node_center(dump, value):
    """Center of the first node whose text OR content-desc equals value, else None."""
    pattern = re.compile(
        r'(?:text|content-desc)="' + re.escape(value) + r'"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
    )
    match = pattern.search(dump)
    if not match:
        return None
    x1, y1, x2, y2 = map(int, match.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def wait_for(label, attempts=15, delay=0.8):
    for _ in range(attempts):
        dump = ui_dump(SERIAL)
        center = node_center(dump, label)
        if center:
            return dump, center
        time.sleep(delay)
    raise SystemExit(f"timed out waiting for {label}")


def png_rows(path):
    data = open(path, "rb").read()
    pos = 8
    width = height = 0
    bpp = 4
    idat = b""
    while pos < len(data):
        length = struct.unpack(">I", data[pos : pos + 4])[0]
        chunk_type = data[pos + 4 : pos + 8]
        chunk = data[pos + 8 : pos + 8 + length]
        if chunk_type == b"IHDR":
            width, height, _, color_type = struct.unpack(">IIBB", chunk[:10])
            bpp = 4 if color_type == 6 else 3 if color_type == 2 else 1
        elif chunk_type == b"IDAT":
            idat += chunk
        pos += 12 + length

    raw = zlib.decompress(idat)
    stride = width * bpp
    out = bytearray()
    prev = bytearray(stride)
    for y in range(height):
        line = bytearray(raw[y * (stride + 1) + 1 : (y + 1) * (stride + 1)])
        filter_type = raw[y * (stride + 1)]
        if filter_type == 1:
            for i in range(bpp, stride):
                line[i] = (line[i] + line[i - bpp]) & 255
        elif filter_type == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 255
        elif filter_type == 3:
            for i in range(stride):
                left = line[i - bpp] if i >= bpp else 0
                line[i] = (line[i] + (left + prev[i]) // 2) & 255
        elif filter_type == 4:
            for i in range(stride):
                left = line[i - bpp] if i >= bpp else 0
                up = prev[i]
                up_left = prev[i - bpp] if i >= bpp else 0
                prediction = left + up - up_left
                pa, pb, pc = abs(prediction - left), abs(prediction - up), abs(prediction - up_left)
                prior = left if (pa <= pb and pa <= pc) else (up if pb <= pc else up_left)
                line[i] = (line[i] + prior) & 255
        out += line
        prev = line
    rows = []
    for y in range(height):
        row = out[y * stride : (y + 1) * stride]
        rows.append([(row[i], row[i + 1], row[i + 2]) for i in range(0, stride, bpp)])
    return width, height, rows


def hand_pixels(path, cx, cy, angle_cw_deg):
    """Count hand-colored pixels on the radial band at angle (clockwise from 12)."""
    _, _, rows = png_rows(path)
    theta = math.radians(angle_cw_deg)
    hits = 0
    for radius in range(RING_R0, RING_R1, 2):
        x = cx + round(radius * math.sin(theta))
        y = cy - round(radius * math.cos(theta))
        if not (0 <= y < len(rows)):
            continue
        for dx in range(-14, 15):
            xx = x + dx
            if not (0 <= xx < len(rows[0])):
                continue
            r, g, b = rows[y][xx]
            if abs(r - HAND_RGB[0]) <= TOLERANCE and abs(g - HAND_RGB[1]) <= TOLERANCE and abs(b - HAND_RGB[2]) <= TOLERANCE:
                hits += 1
    return hits


def capture(serial, path):
    """Screencap straight to a file (binary-safe, avoids piping through a string)."""
    cmd = ["adb"]
    if serial:
        cmd += ["-s", serial]
    cmd += ["exec-out", "screencap", "-p"]
    with open(path, "wb") as fh:
        result = subprocess.run(cmd, stdout=fh, stderr=subprocess.PIPE, timeout=60)
    if result.returncode != 0:
        raise SystemExit(f"screencap failed: {result.stderr.decode(errors='replace').strip()}")


def drag_hand(serial, cx, cy, start_angle_deg, end_angle_deg, radius=232):
    """Finger-drag a dial hand along the arc from start to end angle (clockwise from 12)."""
    adb(serial, "shell", "input", "motionevent", "DOWN", str(round(cx + radius * math.sin(math.radians(start_angle_deg)))), str(round(cy - radius * math.cos(math.radians(start_angle_deg)))))
    time.sleep(0.15)
    steps = 16
    for i in range(1, steps + 1):
        a = start_angle_deg + (end_angle_deg - start_angle_deg) * i / steps
        x = round(cx + radius * math.sin(math.radians(a)))
        y = round(cy - radius * math.cos(math.radians(a)))
        adb(serial, "shell", "input", "motionevent", "MOVE", str(x), str(y))
        time.sleep(0.05)
    adb(serial, "shell", "input", "motionevent", "UP", str(round(cx + radius * math.sin(math.radians(end_angle_deg)))), str(round(cy - radius * math.cos(math.radians(end_angle_deg)))))
    time.sleep(0.6)


def expect_hand(path, cx, cy, angle, label):
    pixels = hand_pixels(path, cx, cy, angle)
    if pixels < MIN_LINE_PIXELS:
        raise SystemExit(f"FAIL: {label} - hand not drawn at {angle}deg (line pixels={pixels})")
    print(f"PASS: {label} - hand drawn at {angle}deg (line pixels={pixels})")


def main():
    global SERIAL
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", default="app/build/outputs/apk/standalone/debug/app-standalone-debug.apk")
    parser.add_argument("--serial", default="")
    parser.add_argument("--center", default="540,1032", help="dial center as X,Y (device dependent)")
    parser.add_argument("--keep", action="store_true", help="keep screenshots in the current directory")
    args = parser.parse_args()
    SERIAL = args.serial
    cx, cy = map(int, args.center.split(","))

    adb(SERIAL, "install", "-r", args.apk)
    adb(SERIAL, "shell", "am", "force-stop", PACKAGE)
    activity = adb(SERIAL, "shell", "cmd", "package", "resolve-activity", "--brief", PACKAGE).splitlines()[-1].strip()
    adb(SERIAL, "shell", "am", "start", "-n", activity)
    time.sleep(4)

    _, alarms_tab = wait_for("Alarms")
    tap(SERIAL, *alarms_tab)
    time.sleep(1.5)

    _, add_alarm = wait_for("Add alarm")
    tap(SERIAL, *add_alarm)
    time.sleep(1.5)

    _, pm = wait_for("PM")
    tap(SERIAL, *pm)
    time.sleep(1)

    with tempfile.TemporaryDirectory() as tmp:
        after_toggle = f"{tmp}/after_toggle.png"
        after_drag = f"{tmp}/after_drag.png"
        capture(SERIAL, after_toggle)
        expect_hand(after_toggle, cx, cy, 210, "after PM toggle (hand at 7 o'clock)")

        drag_hand(SERIAL, cx, cy, 210, 0)
        capture(SERIAL, after_drag)
        expect_hand(after_drag, cx, cy, 0, "after drag (hand at 12 o'clock)")

        if args.keep:
            import shutil
            shutil.copy(after_toggle, "alarm_dial_after_toggle.png")
            shutil.copy(after_drag, "alarm_dial_after_drag.png")

    print("OK: alarm dial hand verified on device.")


if __name__ == "__main__":
    main()