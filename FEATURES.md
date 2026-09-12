# Somn — Full Feature List

Every feature below is built and working. The README carries the short version;
this file is the complete inventory.

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
- **Anti-snore nudge** — gentle vibration when the app detects snoring
- **Wind-down exercises** — breathing, cognitive shuffle and ADHD cooldown routines
- **Health Connect integration** — optional, off by default — reads vitals and writes sleep sessions
- **Encrypted NAS backup** — optional, off by default — AES-256-GCM under a passphrase only you hold
- **CSV & JSON export** — share sleep data with clinicians
- **Sleep as Android import** — bring your history across
- **Tag system** — custom tags for sessions
- **Material 3 / Dynamic Color** — supports Material You theming on Android 12+
- **Offline by default** — no account, no cloud, no app-authored telemetry. Somn uploads
  nothing unless you explicitly set up NAS backup

## On the Roadmap

Tracked in the issue tracker: F-Droid release, wearables beyond Health Connect,
brain-health insights, couples mode, widgets, light (no-sensing) mode, tunable snore
detection, automatic export backups.
