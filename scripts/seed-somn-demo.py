#!/usr/bin/env python3
"""
seed-somn-demo.py — fabricates Somn demo state inside a pulled Room DB.

Run from the directory containing `sleep_tracker.db` (pulled from the device by
seed-somn-demo.sh). Idempotent: wipes user_profile, sleep_sessions and their
children (sleep_epochs, audio_events, habit_logs, external_vitals, tags,
session_tags, alarms) first.

All values are FABRICATED — no real device data is read or written. Percentages
are stored on the 0-100 scale (matches SleepTrackingViewModel.buildCompletedSession).
All RNG is seeded per session, so re-runs produce identical rows.

Timezone: set TZ_NAME to the target device's `getprop persist.sys.timezone`.
"""
import os
import random
import sqlite3
import zoneinfo
from datetime import datetime, timedelta

# The device timezone is passed by seed-somn-demo.sh via SOMN_SEED_TZ
# (getprop persist.sys.timezone) so timestamps are right on any device.
TZ_NAME = os.environ.get("SOMN_SEED_TZ") or "Asia/Kolkata"
# Profile variant: "male-default" (baseline) or "female-cycling" (cycle UI).
PROFILE = os.environ.get("SOMN_SEED_PROFILE") or "male-default"

db = sqlite3.connect("sleep_tracker.db")
c = db.cursor()
tz = zoneinfo.ZoneInfo(TZ_NAME)
today = datetime.now(tz).date()
EPOCH_MS = 30_000


def epoch(y, m, d, hh, mm):
    return int(datetime(y, m, d, hh, mm, tzinfo=tz).timestamp() * 1000)


# ── Idempotent wipe of demo rows (children first, then parents) ─────────────
c.execute("DELETE FROM session_tags")
c.execute("DELETE FROM audio_events")
c.execute("DELETE FROM sleep_epochs")
c.execute("DELETE FROM habit_logs")
c.execute("DELETE FROM external_vitals")
c.execute("DELETE FROM sleep_sessions")
c.execute("DELETE FROM tags")
c.execute("DELETE FROM alarms")
c.execute("DELETE FROM user_profile")
c.execute("DELETE FROM sqlite_sequence WHERE name IN "
          "('sleep_sessions','sleep_epochs','audio_events','habit_logs','tags','alarms')")

# ── 1. fabricated profile (singleton id=1) ──────────────────────────────────
if PROFILE == "female-cycling":
    # FEMALE + CYCLING: cycle features on. Last period started 17 days ago so the
    # session window (today-7 .. today-1) spans FOLLICULAR -> OVULATION -> LUTEAL
    # and today's phase is LUTEAL — the Trends cycle bands show all three colors.
    last_period = (today - timedelta(days=17)).strftime("%Y-%m-%d")
    profile_values = (1, "1994-05-17", "FEMALE", "CYCLING", "MODERATE_MORNING", 18,
                      0, 0, 0, 8.0, None, None, 28, last_period, TZ_NAME, 1)
else:
    profile_values = (1, "1994-05-17", "MALE", "DEFAULT", "MODERATE_MORNING", 18,
                      0, 0, 0, 8.0, None, None, 28, None, TZ_NAME, 1)
c.execute(
    """INSERT INTO user_profile (id, dateOfBirth, biologicalSex, lifeStage, chronotype,
      chronotypeMeqScore, adhdMode, asdMode, medicationTracking, targetSleepHours,
      pregnancyTrimester, pregnancyDueDate, cycleLength, lastPeriodStartDate,
      timezoneId, onboardingCompleted)
      VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
    profile_values,
)

# ── 2. seven fabricated sessions, last 7 nights (today-7 .. today-1) ────────
# (dur_min, in_bed, eff, onset, wakes, deep%, light%, rem%, score, mood, alarm,
#  brpm, coughs, oversleep, home, note)
sessions = [
    (400, 470, 85.0, 22, 3, 20.0, 58.0, 22.0, 62, 3, 1, 15.2, 0, 0, 0, "Travel day, late dinner"),
    (320, 420, 76.0, 35, 6, 12.0, 63.0, 25.0, 45, 2, 1, 16.8, 2, 0, 1, "Restless night, woke often"),
    (435, 480, 91.0, 12, 2, 24.0, 52.0, 24.0, 78, 4, 0, 14.6, 0, 0, 1, "Solid night after wind-down"),
    (365, 460, 79.0, 28, 5, 16.0, 60.0, 24.0, 55, 3, 1, 15.9, 1, 0, 1, "Phone left on nightstand"),
    (555, 585, 95.0, 9, 1, 28.0, 46.0, 26.0, 88, 5, 0, 14.1, 0, 1, 1, "Slept past target after long week"),
    (415, 470, 88.0, 18, 3, 22.0, 54.0, 24.0, 68, 4, 1, 15.4, 0, 0, 1, "Deep sleep nudge earned"),
    (450, 480, 94.0, 10, 2, 26.0, 50.0, 24.0, 84, 4, 1, 14.8, 0, 0, 1, "Consistent with target"),
]

night_dates = [today - timedelta(days=7 - i) for i in range(7)]
session_ids = []
for i, (dur, inbed, eff, onset, wakes, deep, light, rem, score, mood, alarm,
        brpm, cough, over, home, note) in enumerate(sessions):
    night = night_dates[i]
    start = epoch(night.year, night.month, night.day, 22, 45)
    c.execute(
        """INSERT INTO sleep_sessions (startTimeMillis, endTimeMillis, sleepDurationMinutes,
          timeInBedMinutes, sleepEfficiency, sleepOnsetMinutes, wakeEvents, deepSleepPercent,
          lightSleepPercent, remSleepPercent, sleepScore, moodRating, notes, isCompleted,
          timezoneId, isHomeSleep, alarmUsed, avgBreathingRateBrpm, coughEventCount,
          isPartial, sessionType, isOversleep, healthConnectRecordId)
          VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,1,?,?,?,?,?,0,'MAIN_SLEEP',?,NULL)""",
        (start, start + inbed * 60000, dur, inbed, eff, onset, wakes, deep, light, rem,
         score, mood, note, TZ_NAME, home, alarm, brpm, cough, over),
    )
    session_ids.append(c.lastrowid)

# ── 3. per-session sleep epochs (30s cadence, stage % matched to the session) ─
def movement_for(stage, rng):
    if stage == "AWAKE":
        return round(rng.uniform(0.5, 1.8), 2), round(rng.uniform(0.5, 1.0), 2)
    if stage == "LIGHT":
        return round(rng.uniform(0.15, 0.5), 2), round(rng.uniform(0.2, 0.6), 2)
    if stage == "DEEP":
        return round(rng.uniform(0.02, 0.15), 2), round(rng.uniform(0.05, 0.25), 2)
    if stage == "REM":
        return round(rng.uniform(0.05, 0.25), 2), round(rng.uniform(0.1, 0.4), 2)
    return 0.1, 0.1

epoch_count = 0
for i, (sid, (dur, inbed, eff, onset, wakes, deep, light, rem, *_)) in enumerate(zip(session_ids, sessions)):
    rng = random.Random(900 + i)
    start = epoch(night_dates[i].year, night_dates[i].month, night_dates[i].day, 22, 45)
    t = start
    rows = []

    # Onset: awake while lying in bed before first sleep stage.
    for _ in range(onset * 2):
        mv, vv = movement_for("AWAKE", rng)
        rows.append((sid, t, "AWAKE", mv, vv))
        t += EPOCH_MS

    # Remaining epochs: sleep stages sized to the session's deep/light/rem %
    # plus AWAKE bursts sized to wakeEvents.
    remain = inbed * 2 - onset * 2
    sleep_total = round(dur * 2)
    awake_total = remain - sleep_total
    deep_n = round(sleep_total * deep / 100.0)
    rem_n = round(sleep_total * rem / 100.0)
    light_n = sleep_total - deep_n - rem_n

    stages = []
    pool = {"LIGHT": light_n, "DEEP": deep_n, "REM": rem_n}
    cycle = ["LIGHT", "DEEP", "LIGHT", "REM"]
    while pool["LIGHT"] > 0 or pool["DEEP"] > 0 or pool["REM"] > 0:
        for s in cycle:
            if pool[s] > 0:
                stages.append(s)
                pool[s] -= 1

    awake_used = 0
    if awake_total > 0 and wakes > 0 and stages:
        for bp in sorted(rng.sample(range(len(stages)), min(wakes, len(stages)))):
            blen = min(rng.randint(2, 6), awake_total - awake_used)
            if blen <= 0:
                continue
            stages[bp:bp] = ["AWAKE"] * blen
            awake_used += blen
    if awake_total - awake_used > 0:
        stages.extend(["AWAKE"] * (awake_total - awake_used))

    for s in stages:
        mv, vv = movement_for(s, rng)
        rows.append((sid, t, s, mv, vv))
        t += EPOCH_MS

    c.executemany(
        "INSERT INTO sleep_epochs (sessionId, timestampMillis, stage, movementMagnitude, movementVariability) "
        "VALUES (?,?,?,?,?)",
        rows,
    )
    epoch_count += len(rows)

# ── 4. audio events (aligned with each session's coughEventCount + realism) ──
# session-number -> [(type, count), ...]; TALK events have no clipPath so they
# only show in the summary counts, never a broken play button.
audio_plan = {
    2: [("COUGH", 2), ("SNORE", 1)],
    3: [("SNORE", 3)],
    4: [("COUGH", 1), ("SNORE", 1)],
    5: [("SNORE", 1), ("COUGH", 1)],
    6: [("SNORE", 2)],
    7: [("SNORE", 2), ("TALK", 1)],
}
audio_count = 0
for num, plan in audio_plan.items():
    sid = session_ids[num - 1]
    start = epoch(night_dates[num - 1].year, night_dates[num - 1].month, night_dates[num - 1].day, 22, 45)
    end = start + sessions[num - 1][1] * 60000
    rng = random.Random(1400 + num)
    for etype, cnt in plan:
        for _ in range(cnt):
            # Keep events clear of the session edges; guard against short sessions
            # (a nap) where the 30-min margins would invert the range.
            lo = start + 30 * 60000
            hi = end - 30 * 60000
            if hi <= lo:
                lo, hi = start + 1000, end - 1000
            if hi <= lo:
                continue
            ts = int(rng.uniform(lo, hi))
            c.execute(
                "INSERT INTO audio_events (sessionId, timestampMillis, durationSeconds, type, "
                "intensityDecibels, clipPath, syncedToNas) VALUES (?,?,?,?,?,NULL,0)",
                (sid, ts, rng.randint(2, 8), etype, rng.randint(42, 68)),
            )
            audio_count += 1

# ── 5. external vitals — per-session aggregates a paired wearable wrote to
# Health Connect (HEALTH-01). Keyed to sleep quality so the Vitals card tells a
# coherent story: the best night (session 5, score 88) has the lowest HR / highest
# HRV / highest SpO2; the restless night (session 2, score 45) the inverse.
# Realistic sleep ranges: avg HR 50-70 bpm, resting 45-60, HRV(RMSSD) 30-80 ms,
# SpO2 95-99% (min 90-96%), skin temp 35.5-36.5 C.
# sourceApp is a package name (UI resolves to a display label via PackageManager;
# falls back to the raw package name when the wearable app isn't installed).
# com.sec.android.app.shealth = Samsung Health's real package — resolves to
# "Samsung Health" on any Galaxy with it installed.
# (session#, avgHR, restingHR, hrvMs, avgSpo2, minSpo2, skinC, sourceApp)
vitals_plan = [
    (1, 61.0, 54.0, 52.0, 97.0, 93.0, 36.1, "com.sec.android.app.shealth"),
    (2, 66.0, 59.0, 38.0, 95.0, 91.0, 36.5, "com.sec.android.app.shealth"),
    (3, 57.0, 50.0, 64.0, 98.0, 95.0, 35.9, "com.sec.android.app.shealth"),
    (4, 63.0, 56.0, 44.0, 96.0, 92.0, 36.3, "com.sec.android.app.shealth"),
    (5, 54.0, 47.0, 78.0, 99.0, 96.0, 35.8, "com.sec.android.app.shealth"),
    (6, 59.0, 52.0, 56.0, 97.0, 94.0, 36.0, "com.sec.android.app.shealth"),
    (7, 55.0, 48.0, 70.0, 98.0, 95.0, 35.9, "com.sec.android.app.shealth"),
]
vitals_count = 0
for (num, avg_hr, rest_hr, hrv, spo2, min_spo2, skin, source) in vitals_plan:
    c.execute(
        "INSERT INTO external_vitals (sessionId, avgHeartRateBpm, restingHeartRateBpm, "
        "avgHeartRateVariabilityMs, avgSpo2Percent, minSpo2Percent, "
        "avgSkinTemperatureCelsius, sourceApp) VALUES (?,?,?,?,?,?,?,?)",
        (session_ids[num - 1], avg_hr, rest_hr, hrv, spo2, min_spo2, skin, source),
    )
    vitals_count += 1

# ── 6. tags + session_tags — a couple of tags attached to the nights they
# describe (TAG-01). The UI currently has no tag-chip surface wired to these
# tables, but the repository is ready; seeding keeps the data-layer demo state
# complete. Colors are ARGB longs, icons are free-form Material icon names.
tags_plan = [
    ("Travel", "Lifestyle", 0xFFF9A825, "flight", 0),
    ("Restless Night", "Quality", 0xFFD32F2F, "mood_bad", 0),
    ("Wind-down", "Habit", 0xFF388E3C, "self_improvement", 0),
    ("Overslept", "Quality", 0xFF7B1FA2, "bedtime", 0),
]
for (name, cat, color, icon, archived) in tags_plan:
    c.execute(
        "INSERT INTO tags (name, category, color, icon, isArchived) VALUES (?,?,?,?,?)",
        (name, cat, color, icon, archived),
    )
tag_ids = [r[0] for r in c.execute("SELECT id FROM tags ORDER BY id")]
tag_id_by_name = {name: tag_ids[i] for i, (name, *_rest) in enumerate(tags_plan)}
# session-number -> tag names
session_tags_plan = {1: ["Travel"], 2: ["Restless Night"], 3: ["Wind-down"],
                     5: ["Overslept", "Wind-down"], 7: ["Wind-down"]}
session_tag_count = 0
for num, names in session_tags_plan.items():
    for name in names:
        c.execute(
            "INSERT INTO session_tags (sessionId, tagId) VALUES (?,?)",
            (session_ids[num - 1], tag_id_by_name[name]),
        )
        session_tag_count += 1

# ── 7. alarms — one enabled weekday smart alarm + one disabled weekend one, so
# the Alarms list shows both card states (enabled filled / disabled dimmed).
# NOTE: alarms are only scheduled on user actions (create/update/toggle) or a
# device reboot (BootReceiver) — a DB-seeded enabled alarm stays inert until the
# next boot, after which it will genuinely ring at its time. Disable it in the
# app if you don't want a real 7:00 AM ring.
# (hour, minute, label, isEnabled, repeatDays, wakeWindow, snooze, maxSnooze,
#  soundUri, vibration, gradualVolumeSec, captchaType)
alarms_plan = [
    (7, 0, "Workday", 1, "2,3,4,5,6", 30, 9, 3, "", 1, 60, "MATH"),
    (9, 30, "Weekend lie-in", 0, "1,7", 30, 9, 3, "", 1, 60, "NONE"),
]
alarm_count = 0
for (hour, minute, label, enabled, repeat_days, wake, snooze, max_snooze,
     sound, vibration, gradual, captcha) in alarms_plan:
    c.execute(
        "INSERT INTO alarms (hour, minute, label, isEnabled, repeatDays, wakeWindowMinutes, "
        "snoozeDurationMinutes, maxSnoozeCount, soundUri, vibrationEnabled, "
        "gradualVolumeSeconds, captchaType) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
        (hour, minute, label, enabled, repeat_days, wake, snooze, max_snooze,
         sound, vibration, gradual, captcha),
    )
    alarm_count += 1

# ── 8. habit logs — the 7 session nights + today (so Today's Log is populated)
# Stress pattern mirrors the scores (high stress on the bad nights).
stress_pattern = [3, 5, 2, 4, 2, 3, 2, 2]
for i, date_str in enumerate(d.strftime("%Y-%m-%d") for d in night_dates + [today]):
    rng = random.Random(2100 + i)

    def log(**cols):
        # Python 3.12+ sqlite3 raises on missing named params, so every column
        # must be present; unrelated subtype columns default to None.
        base = {
            "date": date_str,
            "entryType": None,
            "caffeineMg": None,
            "caffeineSource": None,
            "alcoholUnits": None,
            "exerciseType": None,
            "exerciseDurationMinutes": None,
            "exerciseIntensity": None,
            "stressLevel": None,
            "medicationName": None,
            "medicationDose": None,
            "medicationIsStimulant": None,
            "timeOfDayHour": None,
            "timeOfDayMinute": None,
            "notes": "",
        }
        base.update(cols)
        c.execute(
            "INSERT INTO habit_logs (date, entryType, caffeineMg, caffeineSource, alcoholUnits, "
            "exerciseType, exerciseDurationMinutes, exerciseIntensity, stressLevel, "
            "medicationName, medicationDose, medicationIsStimulant, timeOfDayHour, "
            "timeOfDayMinute, notes) "
            "VALUES (:date, :entryType, :caffeineMg, :caffeineSource, :alcoholUnits, "
            ":exerciseType, :exerciseDurationMinutes, :exerciseIntensity, :stressLevel, "
            ":medicationName, :medicationDose, :medicationIsStimulant, :timeOfDayHour, "
            ":timeOfDayMinute, :notes)",
            base,
        )

    # Caffeine (morning + optional afternoon)
    log(entryType="CAFFEINE", caffeineMg=95, caffeineSource="COFFEE", timeOfDayHour=8, timeOfDayMinute=30)
    if i in (1, 3, 5):
        log(entryType="CAFFEINE", caffeineMg=47, caffeineSource="TEA", timeOfDayHour=15, timeOfDayMinute=30)
    # Alcohol on the restless nights
    if i in (1, 3):
        log(entryType="ALCOHOL", alcoholUnits=1.5, timeOfDayHour=20, timeOfDayMinute=30)
    # Exercise most mornings
    if i in (0, 2, 4, 6):
        log(entryType="EXERCISE", exerciseType="RUNNING", exerciseDurationMinutes=35,
            exerciseIntensity="MODERATE", timeOfDayHour=7, timeOfDayMinute=0)
    elif i == 5:
        log(entryType="EXERCISE", exerciseType="WALKING", exerciseDurationMinutes=45,
            exerciseIntensity="LIGHT", timeOfDayHour=18, timeOfDayMinute=0)
    # End-of-day stress (no time component, per the domain model)
    log(entryType="STRESS", stressLevel=stress_pattern[i])
    # Daily non-stimulant medication
    log(entryType="MEDICATION", medicationName="Vitamin D3", medicationDose="1000 IU",
        medicationIsStimulant=0, timeOfDayHour=8, timeOfDayMinute=0)

db.commit()
print("profile rows:    ", c.execute("SELECT COUNT(*) FROM user_profile").fetchone()[0],
      f"({PROFILE}: {profile_values[2]}/{profile_values[3]})")
print("session rows:    ", c.execute("SELECT COUNT(*) FROM sleep_sessions").fetchone()[0])
print("scores:          ", [r[0] for r in c.execute("SELECT sleepScore FROM sleep_sessions ORDER BY startTimeMillis")])
print("epoch rows:      ", epoch_count, "(per session:",
      [r[0] for r in c.execute("SELECT COUNT(*) FROM sleep_epochs GROUP BY sessionId ORDER BY sessionId")], ")")
print("audio event rows:", audio_count)
print("vitals rows:      ", vitals_count)
print("tag rows:         ", c.execute("SELECT COUNT(*) FROM tags").fetchone()[0],
      "(names:", [r[0] for r in c.execute("SELECT name FROM tags ORDER BY id")], ")")
print("session-tag rows: ", session_tag_count)
print("alarm rows:       ", alarm_count,
      "(labels:", [r[0] for r in c.execute("SELECT label FROM alarms ORDER BY id")], ")")
print("habit log rows:  ", c.execute("SELECT COUNT(*) FROM habit_logs").fetchone()[0])
db.close()
