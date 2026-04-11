# 🌙 Somn

**A privacy-first, open-source sleep tracker for Android.**

Somn uses your phone's accelerometer to track sleep stages overnight — no wearable required. It scores your sleep with age-calibrated algorithms, supports biological profile adjustments (menstrual cycle, pregnancy, neurodivergent profiles), and keeps everything on-device.

> *"somn"* — from Latin *somnus*, meaning sleep.

---

## ✨ Features

### Built & Working
- **Accelerometer-based sleep tracking** — phone-on-bed motion analysis with 30-second epoch classification
- **Sleep stage classification** — Wake, Light, Deep, REM via movement magnitude & variability
- **Smart sleep scoring** — weighted algorithm (duration, efficiency, stage distribution, consistency, onset latency)
- **Age-calibrated scoring** — adjusts deep sleep targets from 27.5% (teens) to 10% (75+)
- **Biological profile support** — menstrual cycle phase adjustments, pregnancy trimester scoring, ADHD consistency leniency
- **Full onboarding flow** — birth date, biological sex, life stage, chronotype quiz (rMEQ), neurodivergent profile, sleep goals, permissions
- **Smart alarm system** — gradual volume increase, configurable wake window
- **Sleep history & analytics** — session list, hypnogram visualization, detailed session breakdowns
- **Morning review** — post-sleep summary with score ring and stage breakdown
- **Manual session editing** — retroactive session creation and time adjustments
- **CSV export** — share sleep data with clinicians
- **Tag system** — custom tags for sessions
- **Material 3 / Dynamic Color** — supports Material You theming on Android 12+
- **100% offline** — zero network permissions, all data stays on your device

### On the Roadmap
- Habit tracking & lifestyle correlations (caffeine, alcohol, exercise, stress)
- Sleep debt engine with recovery plans
- Circadian intelligence & social jet lag detection
- Audio monitoring (snoring, breathing rate)
- Brain health insights (glymphatic system education)
- Health Connect / wearable integration
- Couples mode
- NAS backup & audio archival

See [TODO.md](TODO.md) for the full development roadmap.

---

## 🏗️ Architecture

Multi-module Android app following **clean architecture** with unidirectional data flow.

```
app/                    → Application entry point, navigation, DI
core/
  ├── data/             → Room database, DAOs, entities, repositories
  ├── domain/           → Models, use cases (pure Kotlin)
  └── ui/               → Design system, theme, shared composables
feature/
  ├── tracking/         → Sleep tracking service, accelerometer, home screen
  ├── alarm/            → Smart alarm system, receivers, firing UI
  ├── analytics/        → History, session detail, charts
  ├── onboarding/       → Multi-step profile setup flow
  └── settings/         → App preferences
```

---

## 🛠️ Tech Stack

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

## 🚀 Build & Run

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
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

---

## 📦 Package Info

| | |
|---|---|
| **Application ID** | `dev.vic41148.somn` |
| **Package** | `dev.vic41148.somn` |
| **Min SDK** | 26 |

---

## 🔒 Privacy

Somn is designed with privacy as a core principle:

- **No internet permissions** — the app cannot make network requests
- **No analytics or tracking SDKs** — zero telemetry
- **All data stored locally** in an on-device Room database
- **Sensitive data** (menstrual cycle, neurodivergent status) never leaves the device
- **No account required** — no sign-up, no cloud sync
- **Open source** — audit the code yourself

---

## 🤝 Contributing

Contributions welcome! This is an early-stage project. Check [TODO.md](TODO.md) for what's next.

1. Fork the repo
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Commit your changes
4. Push and open a PR

---

## 📄 License

This project is licensed under the [GNU General Public License v3.0](LICENSE).

---

<p align="center">
  <sub>Built with 🌙 by <a href="https://github.com/Vic-41148">Vic-41148</a></sub>
</p>
