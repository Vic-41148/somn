# Seeding the Somn Debug Build with Fabricated Demo Data — Checklist

Reproducible procedure to put the app in a known, rich state for re-recording after
UI changes. **Everything seeded is fabricated** — no real device data is read or
recorded. The procedure is DB-level (not UI-driven), so it survives UI redesigns:
the only UI it touches is the app launch itself.

- Script: `scripts/seed-somn-demo.sh` (runs every step below)
- Seeder: `scripts/seed-somn-demo.py` (the fabricated values)
- Dev-doc companion: `tmp/sleep-scoring-measuring-recording.md` (session scratch, gitignored)

---

## What gets seeded

| Item | Value |
|---|---|
| Profile birth date | `1994-05-17` (fabricated) |
| Biological profile | `MALE` (default) — **variant:** `--profile cycling` seeds `FEMALE` + `CYCLING` to exercise the hormonal-phase / cycle UI |
| Life stage | `DEFAULT` (or `CYCLING` for the female variant) |
| Chronotype | `MODERATE_MORNING` (rMEQ 18) |
| Target sleep | 8h 0m |
| Timezone | device `persist.sys.timezone`, passed by the script as `SOMN_SEED_TZ` (fallback: `Asia/Kolkata`) |
| Onboarding | `onboardingCompleted=1` → app boots straight to Home |
| Sessions | **7**, last 7 nights, all `MAIN_SLEEP`, `isCompleted=1` |

Sessions (night → duration → efficiency → score): 6h40m/85%/62 · 5h20m/76%/45 ·
7h15m/91%/78 · 6h05m/79%/55 · **9h15m/95%/88 (oversleep)** · 6h55m/88%/68 ·
7h30m/94%/84. Stage percentages sum to 100 per night; one travel night
(`isHomeSleep=0`); notes on every session; brpm + cough counts vary.

| Item | Value |
|---|---|
| **Sleep epochs** | ~900 per night (30s cadence, full in-bed window): onset AWAKE phase sized to `sleepOnsetMinutes`, sleep stages sized to the night's deep/light/rem %, AWAKE bursts matching `wakeEvents`. Feeds the Morning Review / live hypnograms. |
| **Audio events** | ~15 total across nights (SNORE/COUGH/TALK; counts aligned with each session's `coughEventCount`). TALK rows have no clip, so they show in the summary count but never a broken play button. Feeds the detail screen's Audio Timeline + Audio Events card. |
| **Habit logs** | The 7 session nights **plus today** (so Today's Log is populated) × ~4-5 entries: caffeine (morning coffee / afternoon tea), alcohol on the 2 restless nights, exercise (running/walking), end-of-day stress level (pattern mirrors scores — high stress on the bad nights), daily non-stimulant medication. Feeds the Habits daily log + habit-sleep correlations. |
| **External vitals** | 1 per session (7 total) — HR/HRV/SpO2/skin-temp aggregates a paired wearable wrote to Health Connect (HEALTH-01), keyed to sleep quality: the best night (score 88) has the lowest HR (54 bpm) / highest HRV (78 ms) / best SpO2 (99%), the restless night (score 45) the inverse (66 bpm / 38 ms / 95%). `sourceApp` = `com.sec.android.app.shealth` (Samsung Health's package name; UI resolves the display label via `PackageManager` and falls back to the raw package name when the app isn't installed). Feeds the detail screen's **Vitals** card. |
| **Tags** | 4 tags (Travel, Restless Night, Wind-down, Overslept) with category/color/icon; 6 `session_tags` links to the nights they describe (e.g. the travel night gets Travel, the score-88 night gets Overslept + Wind-down). NOTE: the tag tables + repository are wired, but no screen renders tag chips yet — seeding keeps the data-layer demo state complete for when the UI lands. |
| **Alarms** | 2 rows: an enabled `Workday` 7:00 AM weekday smart alarm (MATH captcha, 30-min wake window) + a disabled `Weekend lie-in` 9:30 AM. The Alarms list shows both card states. **Caution:** a DB-seeded enabled alarm stays inert until the next device reboot, then rings for real — disable it in the app if you don't want a 7:00 AM ring. |

---

## Prerequisites (one-time)

- [ ] One device connected & authorized: `adb devices` shows a single `device`
- [ ] Host tools: `adb`, `sqlite3`, `python3` (sqlite3 stdlib), JDK/Gradle for the build
- [ ] `scripts/seed-somn-demo.sh` and `scripts/seed-somn-demo.py` are present next to each other

## The procedure

1. **Build** — `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
2. **Wipe** — `adb uninstall dev.vic41148.somn` (data intentionally destroyed; the
   debug and release builds share the `dev.vic41148.somn` applicationId, so a debug
   install can't coexist with a signed install)
3. **Install** — `adb install -r app-debug.apk`
4. **Grant** — `pm grant` for `RECORD_AUDIO`, `BODY_SENSORS`, `POST_NOTIFICATIONS`
5. **First launch** — app creates the empty Room DB; do **not** touch the onboarding
   UI; `am force-stop` after ~8s
6. **Pull** — `run-as` → `databases/sleep_tracker.db` (+ `-wal`/`-shm`), checkpoint WAL
7. **Seed** — run `seed-somn-demo.py` (idempotent: wipes `user_profile` +
   `sleep_sessions`, inserts the fabricated rows; Room identity hash untouched)
8. **Push** — `run-as cp` back into `databases/`, delete stale `-wal`/`-shm`
9. **Relaunch** — app opens on Home (onboarding gate satisfied)

## Verification checkpoints (after step 9)

- [ ] Home shows **"Last Night" 7h 30m / score 84** (yesterday's session)
- [ ] History tab lists **exactly 7 cards** with real percentages
      (`94% eff`, `88% eff`, `95% eff`, `79% eff`, `91% eff`, `76% eff`, `85% eff`)
- [ ] Detail screen (Thu card) renders **88 · Great · 9h 15m · 9h 45m in bed ·
      95% eff · 9min onset · Deep 28% / Light 46% · Wakes 1** + note
- [ ] Detail screen shows the **Audio Events** card (Snoring/Coughs/Talking counts)
      and the **Audio Timeline** strip
- [ ] Detail screen shows the **Vitals** card: row 1 = Avg HR / Resting HR / HRV,
      row 2 = SpO2 / Min SpO2 / Skin Temp; source label falls back to the package name
      on a device without the wearable app installed
- [ ] Alarms screen lists **Workday 7:00 AM** (enabled, Smart wake: 30min window, label
      shown) and **Weekend lie-in 9:30 AM** (disabled, dimmed card + switch off)
- [ ] DB sanity: `tags` = 4, `session_tags` = 6, `alarms` = 2
- [ ] Habits tab daily log shows the week of caffeine/alcohol/exercise/stress/
      medication entries; Correlation Insights has sample sizes from habit data
- [ ] DB sanity: `sleep_epochs` ≈ 6.7k rows, `audio_events` = 15,
      `external_vitals` = 7, `tags` = 4, `session_tags` = 6, `alarms` = 2,
      `habit_logs` ≈ 34 (incl. today), all epoch timestamps inside their session window
- [ ] Settings → profile shows the fabricated birth date / MALE / chronotype
- [ ] **Cycle variant** (`--profile cycling`): Trends shows the cycle-phase legend with
      three band colors (Follicular → Ovulation → Luteal across the 7 nights); today's
      phase is LUTEAL (last period = today − 17 days); HormonalPhaseNotifier fires at
      the end of a real session (LUTEAL alert)
- [ ] `dumpsys notification` shows channels registered; no crashes in logcat

## Re-running after UI changes

- [ ] Same command, nothing to update: `bash scripts/seed-somn-demo.sh --yes`
- [ ] Cycle variant: `bash scripts/seed-somn-demo.sh --yes --profile cycling`
      (last period start is computed relative to today, so the phase bands stay correct
      on any run date)
- [ ] Timezone is read automatically from the device (`getprop persist.sys.timezone`)
      via `SOMN_SEED_TZ` — no manual edits needed on any device
- [ ] Dates are computed relative to *today*, so "Last Night" stays correct any week
- [ ] If you want a different placeholder biological profile or birth date: edit the
      two constant lines in `seed-somn-demo.py` (values are on the 0-100 percentage
      scale the app writes — see `SleepTrackingViewModel.buildCompletedSession`)

## Notes / gotchas

- UI changes to screens do **not** break seeding — it never drives the UI except launch.
- The app may show the "Battery Optimization Is On" banner on Home (REL-03 first-run
  notice) — dismiss it before recording; it reappears only on fresh installs.
- Room's schema identity hash (`room_master_table`) is preserved — the DB is created
  by the app itself, we only append/update rows.
- Percentages must stay on the **0–100 scale** (85.0, not 0.85) or History shows "0%".
- `isOversleep` must satisfy `duration > targetSleepMinutes(480) + threshold(60)`,
  i.e. > 540 min, to match the app's own computation (555 min is seeded as oversleep).
