# Somn

**A privacy-first, open-source sleep tracker for Android.**

[![CI](https://github.com/Vic-41148/somn/actions/workflows/ci.yml/badge.svg)](https://github.com/Vic-41148/somn/actions/workflows/ci.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

Somn uses your phone's accelerometer to track sleep stages overnight — no wearable required. It scores your sleep with age-calibrated algorithms, supports biological profile adjustments (menstrual cycle, pregnancy, neurodivergent profiles), and keeps everything on-device.

> *"somn"* — from Latin *somnus*, meaning sleep.

---

## Features

### Built & Working
- **Accelerometer-based sleep tracking** — phone-on-bed motion analysis with 30-second epoch classification
- **Sleep stage classification** — Wake, Light, Deep, REM via movement magnitude & variability
- **Smart sleep scoring** — weighted algorithm (duration, efficiency, deep sleep, consistency, wake events)
- **Age-calibrated scoring** — adjusts deep sleep targets from 27.5% (children and teens) to 10% (75+)
- **Biological profile support** — menstrual cycle phase adjustments, pregnancy trimester scoring, ADHD consistency leniency
- **Full onboarding flow** — birth date, biological sex, life stage, chronotype quiz (rMEQ), neurodivergent profile, sleep goals, permissions
- **Smart alarm system** — gradual volume increase, configurable wake window
- **Sleep history & analytics** — session list, hypnogram visualization, detailed session breakdowns
- **Morning review** — post-sleep summary with score ring and stage breakdown
- **Manual session editing** — retroactive session creation and time adjustments
- **Habit tracking & correlations** — caffeine, alcohol, exercise and stress logged against sleep outcomes
- **Sleep debt engine** — running debt with recovery guidance
- **Circadian intelligence** — chronotype assessment, social jet lag and seasonal analysis
- **Audio monitoring** — snoring, coughing and sleep-talk detection, with optional on-device YAMNet classification and breathing-rate estimation
- **Anti-snore nudge** — gentle vibration when snoring is detected
- **Wind-down exercises** — breathing, cognitive shuffle and ADHD cooldown routines
- **Health Connect integration** — optional, off by default; reads vitals and writes sleep sessions
- **Encrypted NAS backup** — optional, off by default; AES-256-GCM under a passphrase only you hold
- **CSV & JSON export** — share sleep data with clinicians
- **Sleep as Android import** — bring your history across
- **Tag system** — custom tags for sessions
- **Material 3 / Dynamic Color** — supports Material You theming on Android 12+
- **Offline by default** — no account, no cloud, no app-authored telemetry; nothing is uploaded unless you explicitly set up NAS backup

### On the Roadmap
- Brain health insights (glymphatic system education)
- Wearable integration beyond Health Connect
- Couples mode
- F-Droid release

---

## Architecture

Multi-module Android app following **clean architecture** with unidirectional data flow.

```
app/                    → Application entry point, navigation, DI
core/
  ├── data/             → Room database, DAOs, entities, repositories, NAS backup, retention
  ├── domain/           → Models, use cases (pure Kotlin)
  ├── audio/            → Mic/sonar collectors, audio-event classification, breathing rate
  ├── health/           → Health Connect integration
  ├── notifications/    → Notification builders, weekly report worker
  └── ui/               → Design system, theme, shared composables
feature/
  ├── tracking/         → Sleep tracking service, accelerometer, home screen
  ├── alarm/            → Smart alarm system, receivers, firing UI
  ├── analytics/        → History, session detail, charts
  ├── habits/           → Habit logging, correlation & sleep-debt insights
  ├── winddown/         → Pre-sleep breathing and cognitive exercises
  ├── onboarding/       → Multi-step profile setup flow
  └── settings/         → App preferences
```

---

## Tech Stack

| Layer | Tech |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Navigation | Compose Navigation |
| DI | Hilt |
| Database | Room |
| Async | Kotlin Coroutines + Flow |
| Build | Gradle (Kotlin DSL) + Version Catalog |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 |
| AGP | 9.1.0 |

---

## Build & Run

### Install

The easiest way to try Somn is the latest release:

- **v0.1.2** — download `app-release-signed.apk` from the
  [releases page](https://github.com/Vic-41148/somn/releases) and open it on your device
  (Android 8.0 / API 26+). Allow "install unknown apps" when prompted.

> **Do not install v0.1.0.** It is deprecated and its release has been removed: tapping the
> Sleep button (and in some cases Settings) could close the app on Android 14+. Use v0.1.2
> or newer instead.

### Prerequisites
- **Android Studio** Meerkat or later
- **JDK 17**
- **Android SDK 35**

### Steps

```bash
git clone https://github.com/Vic-41148/somn.git
cd somn
```

Open in Android Studio, sync Gradle, and run on a device or emulator (API 26+).

Or build from command line:

```bash
./gradlew assembleStandaloneDebug
```

The APK will be at `app/build/outputs/apk/standalone/debug/app-standalone-debug.apk`.

> `gradle.properties` pins `org.gradle.java.home` to a local path. If your JDK 17 lives
> elsewhere, override it per-invocation with `-Dorg.gradle.java.home="$JAVA_HOME"` rather
> than editing the file.

> Two `channel` flavors ship: `standalone` (default; self-updater reads GitHub Releases) and
> `store` (F-Droid/Izzy; no updater code). The build tasks above use the `standalone` channel —
> the default used for development and CI. To target the store channel, substitute
> `standalone` with `store` throughout (e.g. `assembleStoreDebug`, `testStoreDebugUnitTest`).

### Tests

```bash
./gradlew testStandaloneDebugUnitTest testDebugUnitTest   # 288 unit tests (full suite)
./gradlew lintStandaloneDebug           # Android Lint (standalone channel)
```

CI runs `assembleStandaloneDebug`, `testStandaloneDebugUnitTest testDebugUnitTest` and `lintStandaloneDebug` on every push and PR to `main`
and `dev`, plus guardrails that fail the build if Google Play Services reappear on the release
classpath, if the Auto Backup opt-out is dropped, if `INTERNET` starts being contributed by a
module other than `:core:data`, if emoji appear in any tracked source or docs, or if this
README's (or CONTRIBUTING.md's) unit-test count drifts from the actual suite total.

### Seeding demo data (development)

`scripts/seed-somn-demo.sh` installs the debug build on a connected device and seeds
**fabricated** state — a demo profile, 7 nights of sleep sessions with sleep epochs, audio
events, habit logs, external vitals (HR/HRV/SpO2 as if written by a paired wearable via
Health Connect), tags and a couple of smart alarms — by writing the Room DB directly via
`run-as`. It **uninstalls any existing Somn install on the device first**, refuses to run in
CI, and requires exactly one connected device:

```bash
bash scripts/seed-somn-demo.sh --yes                  # baseline MALE profile
bash scripts/seed-somn-demo.sh --yes --profile cycling  # FEMALE + CYCLING (cycle UI)
```

Re-runs are idempotent (same state every time) — see
`scripts/seed-somn-demo-checklist.md` for the full procedure and verification steps.

---

## Package Info

| | |
|---|---|
| **Application ID** | `dev.vic41148.somn` |
| **Package** | `dev.vic41148.somn` |
| **Min SDK** | 26 |

---

## Privacy

Somn is designed with privacy as a core principle:

- **No analytics, no crash reporting, no ads** — Somn contains no telemetry of its own and
  never reports anything about you to us or to anyone else
- **All data stored locally** in an on-device Room database
- **Excluded from Google's Auto Backup** — your sleep database and any sleep-talk recordings
  are never uploaded to Google Drive, and are not copied during device-to-device transfer
- **Sensitive data** (menstrual cycle, pregnancy, neurodivergent status) never leaves the device
- **No account required** — no sign-up, no vendor cloud
- **Optional NAS backup** — off by default. If you turn it on, backups go only to the
  self-hosted server you configure, encrypted with AES-256-GCM under a passphrase only you hold
- **Sleep-talk recordings expire** — deleted automatically after 7 days by default, with a
  one-tap "delete all recordings" control
- **No Google Play Services** — the release build has zero GMS artifacts on its classpath. QR
  scanning uses [zxing-cpp](https://github.com/zxing-cpp/zxing-cpp), not ML Kit
- **`INTERNET` is declared by one module** (`core:data`) for one feature (NAS backup), and
  nothing else in the app makes network requests
- **Open source** — GPL-3.0; audit the code yourself

Full details — every stored field, every permission, and how to delete anything — are in
[PRIVACY.md](PRIVACY.md).

---

## Contributing

Contributions welcome! This is an early-stage project — see **On the Roadmap** above for what's next.

Please read [**CONTRIBUTING.md**](CONTRIBUTING.md) first — it covers how to get
the project building, how to open a pull request against `dev`, what CI checks
must pass, and the project's coding and privacy conventions.

Quick start:

1. Fork the repo
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Commit your changes
4. Push and open a PR against `dev`

---

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).

---

<p align="center">
  <sub>Built by <a href="https://github.com/Vic-41148">Vic-41148</a></sub>
</p>
