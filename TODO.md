# 🌙 Somn — Development Roadmap

> Derived from **Master Research Document v3** (April 2026)
> Last updated: 9 April 2026

---

## Phase 1 — Foundation Hardening & Onboarding ✅ COMPLETE

Everything here is built and compiling.

### What was done:

- [x] **Biological Profile Domain Layer**
  - `UserProfile.kt` — age, sex, life stage, chronotype, neurodivergent flags, pregnancy/cycle data
  - `MenstrualCyclePhase.kt` — 5 phases with adaptive boundaries based on actual cycle length
  - `SleepScoreAdjustment.kt` — transparent raw → adjusted score with explanation reasons
  - Enums: `BiologicalSex`, `LifeStage`, `Chronotype`, `NeurodivergentProfile`

- [x] **Profile Data Layer**
  - `UserProfileEntity.kt` — Room entity (flat columns, string-serialised enums)
  - `UserProfileDao.kt` — singleton CRUD + convenience updates (period, trimester)
  - `UserProfileRepository.kt` — domain mapping + cycle phase/day helpers
  - `SleepDatabase.kt` — bumped to v2, added entity + DAO
  - `DataModule.kt` — DI wiring + destructive migration (dev-only)

- [x] **Age-Calibrated Sleep Scoring**
  - `CalculateSleepScoreUseCase.kt` — full rewrite
    - Backward-compatible `invoke()` for existing code
    - New `calculateWithProfile()` with:
      - Age-calibrated deep sleep targets (27.5% teens → 10% for 75+)
      - Menstrual phase adjustments (+5 to +10 pts)
      - Pregnancy trimester adjustments (+2 to +10 pts)
      - Perimenopause/menopause wake tolerance
      - Postpartum fragmentation adjustment (+8 pts)
      - ADHD consistency leniency (1.5x tolerance)
      - All adjustments capped at +20, with plain-language explanations

- [x] **Onboarding Flow** (`feature:onboarding` — new module)
  - `OnboardingViewModel.kt` — multi-step state, intelligent step skipping
  - `OnboardingFlow.kt` — AnimatedContent slide transitions + progress bar
  - `WelcomeScreen.kt` — tagline + privacy commitment
  - `BirthDateScreen.kt` — date picker with age-calibration explanation
  - `BiologicalSexScreen.kt` — gates hormonal feature visibility
  - `LifeStageScreen.kt` — dynamic sub-inputs (cycle slider, period date, trimester)
  - `NeurodivergentScreen.kt` — ADHD/ASD toggles with respectful framing
  - `ChronotypeQuizScreen.kt` — 5-question reduced MEQ
  - `SleepGoalScreen.kt` — large display + slider with age recommendation
  - `PermissionsScreen.kt` — per-permission explanations, mic optional
  - `OnboardingCompleteScreen.kt` — profile summary before save

- [x] **Manual Session Editing**
  - `ManualSessionUseCase.kt` — create retroactive sessions, adjust times, extend sessions

- [x] **Sensor Diagnostic & Awake Detection**
  - `SensorDiagnosticUseCase.kt` — accelerometer/mic capability check
  - `AccelerometerCollector.kt` — enhanced with phone-lifted + significant motion detection

- [x] **Navigation & Wiring**
  - `SleepNavGraph.kt` — conditional start destination (onboarding vs home)
  - `MainActivity.kt` — observes onboarding state from DB
  - `settings.gradle.kts` + `app/build.gradle.kts` — onboarding module registered
  - `gradle.properties` — pinned to JDK 17

---

## Phase 2 — Habit Tracking & Sleep Debt 🔜 NEXT

### To build:

- [ ] **Habit Log Domain & Data**
  - [ ] `HabitLog.kt` — caffeine, alcohol, stress, exercise, medication entries
  - [ ] `CaffeineEntry`, `AlcoholEntry`, `ExerciseEntry`, `MedicationEntry` models
  - [ ] `HabitLogEntity.kt` — Room entity with type converters
  - [ ] `HabitLogDao.kt` — CRUD + date range queries
  - [ ] `HabitLogRepository.kt` — domain mapping, correlation queries

- [ ] **Sleep Debt Engine**
  - [ ] `SleepDebtUseCase.kt` — rolling 14-day debt calculation
  - [ ] `SleepDebt.kt` — debt total, trend, level, daily breakdown
  - [ ] `RecoveryPlan.kt` — additional minutes/night, adjusted bedtime, recovery days

- [ ] **Lifestyle Correlation Engine**
  - [ ] `CorrelationUseCase.kt` — caffeine→onset, alcohol→efficiency, stress→wakes, exercise→quality

- [ ] **Habit Tracking UI** (`feature:habits` — new module)
  - [ ] `HabitViewModel.kt`
  - [ ] `DailyLogScreen.kt` — quick evening entry
  - [ ] `MedicationLogScreen.kt` — ADHD stimulant timing (conditional)
  - [ ] `CorrelationInsightsScreen.kt` — visual charts

- [ ] **Sleep Debt UI**
  - [ ] Sleep debt card on `HomeScreen.kt`
  - [ ] `SleepDebtDetailScreen.kt` — 14-day chart + recovery plan

---

## Phase 3 — Circadian Intelligence & Chronotype

- [ ] `ChronotypeAssessmentUseCase.kt` — rMEQ scoring + data-driven detection after 14 nights
- [ ] `SocialJetLagUseCase.kt` — weekday vs weekend midpoint, cardiovascular risk flagging
- [ ] `SeasonalAnalysisUseCase.kt` — winter hypersomnia / summer insomnia, latitude-aware
- [ ] `CircadianInsightsScreen.kt` — social jet lag visual, seasonal trends
- [ ] Timezone change detection in `SleepTrackingService`
- [ ] `timezoneId` and `isHomeSleep` fields on `SleepSessionEntity`

---

## Phase 4 — Audio Monitoring & Smart Alarm

- [ ] `core:audio` module — `AudioCollector`, `SnoreDetector`, `AudioEventClassifier`
- [ ] `AudioEvent.kt` — type, timestamp, duration, intensity
- [ ] Audio event database entities + DAO
- [ ] Integrate `AudioCollector` into `SleepTrackingService` (opt-in)
- [ ] `SmartAlarmUseCase.kt` — sleep-stage-aware alarm within wake window
- [ ] ASD mode: vibration-only alarm
- [ ] `BreathingRateEstimator.kt` — mic-based breathing analysis
- [ ] Audio events timeline overlay on `SessionDetailScreen`
- [ ] Snoring summary on `MorningReviewScreen`

---

## Phase 5 — Intelligent Notifications & Wind-Down

- [ ] `core:notifications` module — `NotificationEngine`, `WeeklyReportGenerator`, `AnomalyDetector`
- [ ] `HormonalPhaseNotifier` — cycle phase alerts
- [ ] `DeepSleepAlertNotifier` — glymphatic framing
- [ ] `PPDRiskNotifier` — postpartum mental health resources after 3+ weeks fragmented
- [ ] `feature:winddown` module
  - [ ] `BreathingExerciseScreen.kt` — 4-7-8 / box breathing with haptic
  - [ ] `CognitiveWindDownScreen.kt` — journaling, worry list, task dump
  - [ ] `ADHDCooldownScreen.kt` — brain disengagement techniques
- [ ] `MorningBriefingWidget.kt` — lock screen score + insight

---

## Phase 6 — Brain Health, Wearables & Advanced Analytics

- [ ] `BrainHealthUseCase.kt` — SWS trend tracking, glymphatic "Brain Detox" framing
- [ ] `GlymphaticEducationCards.kt` — evidence-based education carousel
- [ ] `core:health` module — Health Connect bidirectional sync
  - [ ] HRV, SpO2, heart rate, skin temperature from wearables
  - [ ] `ReadinessScoreUseCase.kt` — composite: sleep + HRV + stress
- [ ] Menstrual cycle overlay on sleep trend charts
- [ ] `TrendsScreen.kt` — multi-metric trends, correlation heatmap, cohort benchmarks

---

## Phase 7 — Couples Mode, Export & Polish

- [ ] `feature:couples` module — device pairing, dual hypnogram, disturbance attribution
- [ ] `DoctorReportUseCase.kt` — clinician-ready PDF export
- [ ] `SleepWrappedUseCase.kt` — annual Spotify Wrapped-style summary
- [ ] Enhanced CSV/JSON export with all data types
- [ ] Dream journal (voice-to-text)
- [ ] Nap optimization
- [ ] UI polish: glassmorphism, micro-animations, score ring fill, OLED theme, Google Fonts

---

## Phase 8 — NAS Integration & Self-Hosted Backup

> **Architecture: Local-first, NAS-optional.** Everything runs on-device by default. NAS is a power-user feature for audio archival and encrypted backup — never required.

- [ ] **NAS Configuration**
  - [ ] `NasConfig.kt` — SMB/NFS/WebDAV connection settings
  - [ ] NAS setup screen in Settings (host, path, credentials, test connection)
  - [ ] Connection health monitoring

- [ ] **Audio Archive Sync**
  - [ ] Keep last 7 days of audio clips on device
  - [ ] WorkManager background sync of older clips to NAS
  - [ ] `syncedToNas` flag on audio events — auto-prune local after confirmed sync
  - [ ] On-demand retrieval from NAS when viewing old sessions

- [ ] **Encrypted Full Backup**
  - [ ] Encrypted DB export (AES-256) to NAS on schedule (daily/weekly)
  - [ ] Backup restore flow — import from NAS to new device
  - [ ] Doctor report PDF auto-save to NAS
  - [ ] Sleep Wrapped media export to NAS

- [ ] **What stays local forever (never needs NAS)**
  - Sleep tracking, scoring, habits, all analytics
  - Audio recording + live ML classification
  - Correlation engine and trend computation
  - Profile and biological data (encrypted at rest on device)

---

## Build Notes

- **JDK**: Project pinned to JDK 17 via `gradle.properties` (`org.gradle.java.home`)
- **DB Migrations**: Using destructive migration during dev — switch to proper migration scripts before release
- **Cycle Data Privacy**: Encrypt menstrual data at rest before any cloud sync features
