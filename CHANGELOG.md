# Changelog

All notable changes, newest first. The `Release` workflow publishes a GitHub
Release for every `v*` tag using the section of this file whose header contains
the tag — keep section headers unique and tag-containing (for example
`## Somn v0.1.2 — ...`).

## Unreleased

- **Release logs carry no paths.** `Log.e` sites that logged clip paths, NAS hosts, remote paths, or backup names now log the exception class only — release builds keep `Log.e`, so anything else would survive stripping.
- **Bounded ingestion everywhere.** One shared `BoundedInputStream` caps NAS listings (2 MB), CSV imports (8 MB), and backup-restore staging (256 MB, pre-checked via provider size); imports now insert atomically so a crash can no longer leave a half-imported history.
- **Release provenance.** Every GitHub release now ships a CycloneDX SBOM and a keyless Sigstore bundle over `SHA256SUMS.txt`; the README documents the verify commands. Release signing fixed to the standalone channel.
- **Version derives from the git tag.** `versionCode`/`versionName` come from `git describe` (v0.1.2 → 1002 / "0.1.2") so tags, builds, and the self-updater can never disagree.
- **Declare `ACCESS_LOCAL_NETWORK` early.** Inert at targetSdk 36; the runtime request lands in the NAS-setup flow with the targetSdk 37 bump, before Android 17 enforcement breaks WebDAV sync.
- **SQLCipher migrated to `sqlcipher-android` 4.10.0.** The old `android-database-sqlcipher` 4.5.x line is end-of-life and its natives are 4KB-aligned; the new artifact is 16KB-aligned on all ABIs (verified with `readelf` on the release APK). Package `net.sqlcipher.database` → `net.zetetic.database.sqlcipher`, `SupportFactory` → `SupportOpenHelperFactory`, native load via `System.loadLibrary` once.
- **Audio timeline + recordings rebuilt.** Timeline is now a labeled card (legend with counts, start/end times, loudness-scaled markers, tap-to-select with TalkBack summary) and clips play through one proper player: speech routing, play/pause/stop, progress, and a visible error when a file will not play. All kept clips (talk, snore, cough) are listed.
- **Backup integrity + sealed portable backups.** PBKDF2 iterations from backup headers are clamped, restores run `integrity_check` plus a table allowlist (no triggers/views), staging is size-capped, and passphrase backups now envelope a plaintext export so they restore on installs with a different key. Release builds strip `Log` calls.
- **Updater verifies what it installs.** Downloads now check the APK signature against the installed app (checksum alone is not authenticity), release-note hash fallback is gone, downloads stay on the GitHub host allowlist across redirects, and APKs are size-capped. Dead updater unit tests are wired back into CI.
- **At-rest encryption.** The Room DB is now SQLCipher-encrypted with a Keystore-wrapped random key (existing installs migrate their plaintext DB in place), sensitive prefs (NAS endpoint, QR value, backup URI, menopause answers) are sealed, and new sleep audio clips are AES-GCM sealed (legacy clips keep playing until retention prunes them).
- **Alarms survive reboot + no more plaintext backups in shared storage.** `BootReceiver` is now registered, so enabled alarms re-arm after a reboot. The mandatory pre-update backup stays in app-private storage only — the mirror to public Downloads is gone, so no plaintext backup lands in shared storage anymore.
- **UI standardization pass.** Settings toggles and radio rows have consistent gaps, icon buttons use real spacers instead of leading spaces, back navigation is an arrow everywhere, gutters and rhythm follow the 4/8/12/16/24 scale, and Battery Threshold uses the same slider as the other thresholds. History rings and the Trends chart replay their entrance animation on every range or metric switch.
- **History header rings match Home.** Same 72dp size and short labels, so the two ring strips read as one component.
- **Plain-language pass over all user-visible text and code comments.** Every string the user sees (onboarding, Home, alarm, analytics, habits, settings, wind-down, notifications) and every code comment now follows the same plain-language rules: active voice, no contractions, short common words. Copy only — no behavior or meaning changed.
- **Automated QA guardrails.** The alarm dial's AM/PM-toggle rebuild policy is now covered by unit tests (was only verified by hand on a device), the toggle-then-drag hand check itself is automated in `scripts/verify_alarm_dial.py` (one command, screenshots + pixel assertion), and CI now fails a source-touching commit that does not also update CHANGELOG.md.
- **Alarm dial hand no longer vanishes (fixed).** On the alarm time picker, toggling AM/PM and then dragging the hand could make the hand stop drawing (numbers stayed, time still moved) until the screen was reopened. The picker is now rebuilt with fresh state every time AM/PM flips while no finger is down, so the orphaned hand animation can't survive a toggle.
- **Health Connect steps & exercise (R6).** Prior-day step count and active minutes now feed the morning readiness verdict — a "Yesterday's activity" contributor (scored toward 10k steps / 45 active minutes) and a matching Outlook sentence. Degrades gracefully: with no Health Connect data the contributor is skipped, never fabricated as a quiet day.
- **Cycle depth (R5).** New menopause check-in (10-symptom questionnaire, stored on-device) for peri/menopause users, plus luteal-phase coaching in the daily Outlook and a luteal-window extra-minutes hint for the debt recovery plan. Phase refinement now also corrects the calendar estimate with overnight skin temperature when Health Connect has it.
- **Correlations & predictors (R4).** Settled-correlation insight sentences in the daily Outlook, per-tag shift flags, and expanded tag predictors.
- **In-app reports with on-device PDF (R3).** Weekly/monthly/year reports with Save PDF and Share PDF, generated entirely on-device.
- **Vitals dashboard & Rest Mode (R2).** Wearable-vitals dashboard, a Rest Mode that keeps sick nights from moving streaks/baselines, and per-category data purge.

## Somn v0.1.2 — smarter scoring, reliable alerts

**A quality release: smoother and more accurate sleep staging, the habit-tap crash fixed, the chronotype quiz corrected to the real rMEQ scale, and a batch of reliability hardening — including a race that could silently drop the morning deep-sleep alert.**

### What's new

- **Smoother, more accurate sleep staging.** Stage classification now runs through a rolling 3-epoch window in the live tracking path, so a stray misread epoch no longer flickers the whole night between Wake/Light/Deep/REM — and the final moments of a session are always counted (the held-back final epoch is recovered even if a session ends mid-stop).
- **Chronotype quiz corrected.** The morning/evening quiz now uses the real rMEQ scale (4-25). Previous releases used the 19-item Horne-Ostberg bands (16-86) on rMEQ scores, which made every morning band structurally unreachable and mislabeled genuine morning types as evening. Stored results are rescaled automatically on load.
- **Habit insights with confidence.** Correlation cards now show how confident a finding is (Low/Medium/High based on the number of nights) alongside the sample size — a strong-looking r computed from 7 nights is no longer presented as a settled result.
- **Seasonal analysis respects where you live.** A new hemisphere override for the circadian/seasonal insights, for users whose schedule deliberately breaks from their geographic latitude.

### What's fixed

- **Habit-tap crash (caffeine / alcohol / stress).** Tapping a habit row in Daily Log could crash the app — a Compose `FlowRow` runtime-signature mismatch between the app's runtime and what modules compiled against. The whole compose stack is now aligned on one BOM, with a CI guardrail that fails the build if the resolved foundation version ever drifts from what modules compile against again.
- **Alarm firing screen is reachable again.** The in-app full-screen firing experience only existed as a registered-but-never-navigated route. The app now navigates to it whenever the alarm service reports firing, including after snooze.
- **Per-alarm wake windows honored.** Each alarm's wake-confirmation window (10-45 minutes via the edit screen slider, 30 by default) now flows through the entire firing chain instead of falling back to the global default; the smart-fire and snooze re-arm paths respect it too. An NFC captcha option was added to the firing screen.
- **Onboarding refinements.** Life-stage options were pruned to the ones that actually affect your scoring, with an explicit "prefer not to say" choice; the recommended sleep-hours display is fixed.
- **Deep-sleep alert race.** The morning deep-sleep alert ("Brain Detox Interrupted") shared its notification id with the tracking service's foreground notification, so ending a session could delete the alert about half the time. It now uses its own id — the device test suite caught this one and now guards it (the same audit also moved the PPD and weekly-report notifications off ids owned by foreground services).
- **Back button no longer strands a live session.** Pressing back during tracking used to pop you back to Home with the recording still running and no way to stop it (the Home moon only starts sessions). Back is now locked while a session is active — the stop goes through Wake Up — and the Home moon returns you to the tracking screen if you ever land there mid-session.
- **Morning review shows the right night.** The review screen now renders the session it was opened for; a relaunch mid-flow could previously show a stale session from a previous night.
- **Tracking notification tap-through.** Tapping the ongoing "Sleep Tracking Active" notification now opens the tracking screen directly (previously it did nothing), so the Wake Up button is reachable from anywhere — even if you left the app mid-session.

### What else changed

- Onboarding, alarm, and notification paths hardened; a crash-path fix ensures an interrupted stop can't lose the newest epoch.
- 15-step release preflight, now scripted and versioned (`scripts/run-release-preflight.sh`): boots the emulator, seeds demo data, and runs the full device test suite end-to-end (FGS smoke, session e2e with morning alerts, wake-window e2e, habits forms, trends, vitals, alarms) — all 15 passed on this release.
- Docs refreshed (README, PRIVACY, CONTRIBUTING, Play description) to match the code; CI now fails if the advertised unit-test count drifts from the real suite total.
- Still privacy-first: on-device sleep tracking, no account, no cloud, no analytics, no ads, no Google Play Services in the release build.

### Verified

- Full 15/15 device preflight on the emulator (fresh seed -> UI verifies -> session e2e -> release build), including the morning-alert e2e that regresses the deep-sleep race.
- `assembleRelease` clean (R8 minification, zero missing-member warnings); compose-foundation alignment guardrail green.
- 193 unit tests, lint gate green.

### Install

Download `app-release-signed.apk` and open it on your device (Android 8.0 / API 26+). Allow "install unknown apps" when prompted.

### Checksum

The release pipeline computes the signed APK's SHA-256 automatically; it is attached to this release as `SHA256SUMS.txt` in the assets below.

### Links

- [README](https://github.com/Vic-41148/somn/blob/v0.1.2/README.md)
- [PRIVACY.md](https://github.com/Vic-41148/somn/blob/v0.1.2/PRIVACY.md)
- [Issues](https://github.com/Vic-41148/somn/issues)

**License:** GPL-3.0
