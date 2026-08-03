# Somn dev/test scripts

Reusable device scripts for the repeatable dev workflows that used to be one-off
ad-hoc commands. These started as scratch in `tmp/dev-scripts/` and were promoted
here so they're tracked and versioned with the app. `seed-somn-demo.sh` + `seed-somn-demo.py`
are the repo's device-seeding tools (see `seed-somn-demo-checklist.md`); the rest are the
verification suite.

All scripts need: one connected adb device, host `sqlite3` + `python3`, and the
`seed-somn-demo.sh` repo seeder (for the seeding ones).

## Quick start

```bash
# pristine demo, one command:
scripts/reset-demo.sh            # MALE baseline
scripts/reset-demo.sh cycling    # FEMALE+CYCLING, today = LUTEAL

# prove the Luteal alert fires end-to-end (real start/stop via the UI):
scripts/run-session-e2e.sh                        # seeds cycling, asserts alert
scripts/run-session-e2e.sh --profile default      # asserts NO hormonal alert
scripts/run-session-e2e.sh --no-seed --duration 40 --retries 2
```

## Scripts

| Script | What it does |
|---|---|
| `lib.sh` | Shared helpers — wake/unlock, launch fresh, uiautomator find+tap, DB pull/push via run-as, notification assert, live logcat. Source, don't run. |
| `seed-demo.sh [default\|cycling]` | Wrapper around the repo `scripts/seed-somn-demo.sh` + post-seed sanity check. |
| `reset-demo.sh [default\|cycling]` | One-command pristine state: seed → verify → cleanup → phase summary. |
| `run-session-e2e.sh` | THE morning-alert test: starts a real tracking session via the UI, stops it, asserts the session completed, deep-sleep alert (1001) + Luteal alert (1003) posted, FGS stopped. Options: `--profile default\|cycling`, `--no-seed`, `--duration N`, `--retries N`. |
| `verify-db-phase.sh [--expect PHASE]` | Replicates `MenstrualCyclePhase.currentPhase` + the Trends band runs against the on-device DB; prints today's phase; `--expect LUTEAL` asserts it. |
| `verify-cycle-legend.sh [male\|cycling]` | Asserts the Trends "Cycle phase" legend renders (cycling) or is absent (male) — the UI-gating check. |
| `verify-vitals.sh` | Asserts the detail-screen Vitals card renders the seeded external_vitals (HR/HRV/SpO2/skin temp). |
| `verify-alarms.sh` | Asserts the Alarms screen shows both seeded alarms. |
| `verify-trends.sh` | Dumps the Trends screen texts (informational). |
| `verify-habits-forms.sh` | REGRESSION: taps all four Daily Log habit sections (Caffeine → Alcohol → Exercise → Stress), asserts each form expands and renders its content, and that no FATAL lands in logcat. Guards the compose foundation FlowRow NoSuchMethodError crash. |
| `verify-release-pipeline.sh` | Pre-release check: `assembleRelease` → compose-foundation alignment guardrail → resolved foundation on the release runtime classpath → R8 clean (no missing-member warnings). |
| `smoke-fgs.sh [--duration N] [--no-cleanup]` | Quick FGS smoke: start → confirm service up + no crash → stop → clean the junk session. |
| `cleanup-junk-sessions.sh [--keep N] [--yes]` | Deletes sessions created after the seeded nights (default keeps the first 7) children-first, pushes the DB back. Always lists what it deletes first. |

## Gotchas learned the hard way (encoded in the scripts)

- **Secure lock screen**: if the phone locks itself, adb can't bypass a PIN/face
  lock. `wake_and_unlock` fails loudly — unlock the phone and re-run. Scripts set
  `svc power stayon true` while charging so it won't re-lock mid-run.
- **Logcat rotation**: Samsung's ring buffer rotates in under a minute — post-hoc
  `adb logcat -d` misses the evidence. `run-session-e2e.sh` streams logcat to a
  host file live.
- **First run after a fresh install is flaky**: JIT/GC warmup can stall the
  teardown coroutine and silently skip the alerts. `run-session-e2e.sh` retries by
  warm-relaunching on the same install (a re-seed/reinstall would just reproduce the
  first-run condition); warm runs are consistent.
- **Fresh process after seeding**: always force-stop + relaunch after pushing the
  DB, or Room serves stale in-memory state.
- **UI coordinates** were validated on the Galaxy S24 FE; `find_center` resolves
  nodes by content-desc/text so taps are mostly layout-agnostic, but the History
  top-card tap in `verify-vitals.sh` is a fixed coordinate.

## The bugs this workflow guards

`run-session-e2e.sh` exists because the Luteal alert was silently never firing: the
service flushed its final epoch with `runBlocking` on the main thread during
teardown, wedging Room's executors so `notifyMorningAlerts` hung after the first
notification. Fixed in `SleepTrackingService`/`SleepTrackingViewModel` — this script
proves the alert posts on every warm run.

`verify-habits-forms.sh` exists because every habit form crashed with
`NoSuchMethodError` on `FlowRow` when tapped: androidx.emoji2's transitive
constraint pushed the app runtime to compose foundation 1.9.0 while modules
compiled against the BOM's 1.7.6, and foundation changed the `FlowRow` signature
between those versions. Fixed by the 2025.08.01 BOM bump + the
`verifyComposeFoundationAlignment` CI guardrail — this script proves all four forms
still expand and render on-device.
