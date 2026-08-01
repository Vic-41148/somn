# Contributing to Somn

Welcome! We're glad you're here. Somn is a privacy-first, open-source sleep
tracker for Android, and it is built by people like you — contributors of every
level are welcome, whether you're fixing a typo, squashing a bug, or building a
whole feature.

**No experience required.** If you've never opened a pull request before,that's fine — this document walks you through the whole flow, and the maintainer is
friendly. Ask for help on any open issue; nobody expects you to know everything
upfront.

---

## Table of contents

- [What we're building](#what-were-building)
- [Ground rules](#ground-rules)
- [Ways to contribute](#ways-to-contribute)
- [Getting started](#getting-started)
- [Finding something to work on](#finding-something-to-work-on)
- [Making a change (step by step)](#making-a-change-step-by-step)
- [Branch protection & CI](#branch-protection--ci)
- [Code style & conventions](#code-style--conventions)
- [Testing](#testing)
- [Privacy guardrails](#privacy-guardrails)
- [Reporting a security issue](#reporting-a-security-issue)
- [Code of conduct](#code-of-conduct)
- [License](#license)

---

## What we're building

Somn tracks sleep using nothing but your phone's accelerometer — no wearable
required. It scores sleep with age-calibrated algorithms, adjusts for biological
profile (menstrual cycle, pregnancy, neurodivergent profiles), and keeps every
byte of data on-device.

The thing that makes Somn different: **privacy is the product**. No accounts, no
cloud, no telemetry, no Google Play Services. Everything you record stays on your
phone unless you explicitly set up encrypted NAS backup.

You can read the full feature list and architecture in the
[README](README.md).

## Ground rules

- **License:** Somn is GPL-3.0. Contributions are made under that license, so by
  submitting a pull request you agree to release your work under it.
- **Privacy first:** never add analytics, crash reporting, advertising, or any
  code that phones home. If a change needs the `INTERNET` permission, it has to
  justify itself — see [Privacy guardrails](#privacy-guardrails).
- **No AI attribution:** commits must never include AI co-author credit or
  "Generated with ..." trailers. Just your real name and your work.
- **Source only:** `docs/` and `.planning/` are intentionally not tracked in git.
  Don't add files from those directories to a pull request.

## Ways to contribute

You don't have to write Kotlin to help:

- **Report bugs** — open an issue with the device, Android version, and what you
  were doing. If you can, attach a log or a screenshot.
- **Suggest features or ideas** — open an issue with the `enhancement` label.
  Ideas are cheap; good reasoning about *why* is gold.
- **Write code** — bugs, tests, and roadmap items (see below).
- **Improve documentation** — the README, this file, or in-code docs.
- **Test on real hardware** — sleep tracking is only as good as the phones it
  runs on. If you have a device, help verify behavior in the real world.
- **Review others' pull requests** — even a "looks good, tested on my Pixel" is
  genuinely valuable.
- **Translate** — the app's strings live in `app/src/main/res/values*/`.

## Getting started

### Prerequisites

- **JDK 17** (required — the build is pinned to it)
- **Android Studio** Meerkat or later, **or** the Android SDK with build tools 35
- An Android device or emulator running **API 26+**

> Note: `gradle.properties` pins `org.gradle.java.home` to a local JDK path. If
> your JDK 17 lives elsewhere, override it per-invocation with
> `-Dorg.gradle.java.home="$JAVA_HOME"` rather than editing the file.

### Building

```bash
git clone https://github.com/Vic-41148/somn.git
cd somn
./gradlew assembleDebug          # debug APK
./gradlew testDebugUnitTest      # unit tests
./gradlew lintDebug              # Android Lint
```

Open the project in Android Studio, sync Gradle, and run on your device.

## Finding something to work on

- Check the **open issues** — anything labeled `good first issue` is a great
  starting point.
- The **On the Roadmap** section of the README lists upcoming work. If you see
  something you'd like to build, say so in the issue tracker first so work isn't
  duplicated.
- **Tests are always welcome.** The algorithmic core (sleep score, stage
  classification, sleep debt, correlations) has grown solid coverage, but more is
  better — and tests don't need design approval.

## Making a change (step by step)

The short version: fork, branch, commit, open a pull request to `dev`.

```bash
# 1. Fork the repo on GitHub, then:
git clone https://github.com/<your-username>/somn.git
cd somn
git remote add upstream https://github.com/Vic-41148/somn.git

# 2. Branch off the latest dev
git fetch upstream
git checkout -b feature/my-feature upstream/dev

# 3. Make your changes, then run the checks
./gradlew testDebugUnitTest -Dorg.gradle.java.home="$JAVA_HOME"
./gradlew lintDebug -Dorg.gradle.java.home="$JAVA_HOME"

# 4. Commit with a clear message (conventional commits preferred)
git add .
git commit -m "feat(tracking): add widget summary of last night's sleep"

# 5. Push and open a pull request against the dev branch
git push -u origin feature/my-feature
```

Then open a pull request **against `dev`** (not `main`). `dev` is where
integration happens; `main` is reserved for releases.

### Commit message style

We use conventional commit prefixes so the history stays readable:

- `feat(scope):` — a new feature
- `fix(scope):` — a bug fix
- `docs:` — documentation only
- `test:` — tests only
- `refactor:` — no behavior change
- `chore:` / `ci:` — housekeeping or CI

Example scopes: `tracking`, `alarm`, `analytics`, `habits`, `settings`,
`onboarding`, `audio`, `data`, `notifications`, `privacy`.

## Branch protection & CI

Both `main` and `dev` are protected on GitHub:

- **Direct pushes are blocked for non-admins.** All changes land via pull
  requests.
- **One approval is required** before a pull request from a non-admin can merge
  (the maintainer will review and merge it for you).
- **CI must pass.** The following checks run on every PR and must be green:
  - `Build, test & lint` — `assembleDebug`, unit tests, and Android Lint
  - `Privacy guardrails` — automated checks that protect the privacy promises
    (details below)
- **Branches must be up to date** before merging, and **force pushes** are
  blocked on protected branches.

If CI fails on your PR, don't worry — read the log, fix the issue, and push
again. The check names appear directly on the PR page.

## Code style & conventions

- **Kotlin + Jetpack Compose + Material 3.** Follow the style you see in the
  existing code.
- **Clean architecture, multi-module.** Keep changes inside the existing module
  boundaries — `core/*` for shared infrastructure, `feature/*` for screens.
  Don't introduce new architectural patterns or new charting/DI/DB libraries
  without discussing it first.
- **No emoji in source or user-facing text.** We deliberately removed them; keep
  it that way.
- **Kotlin-first APIs.** Prefer idiomatic Kotlin (immutability, `Flow`, coroutines)
  over Java-style patterns.
- **Accessibility matters.** New UI should have content descriptions and support
  screen readers, matching existing patterns.

When in doubt, match the surrounding code — consistency beats cleverness.

## Testing

Every pull request is expected to keep the test suite green:

```bash
./gradlew testDebugUnitTest
```

The suite currently runs 133 unit tests. If you add a feature, add tests for it —
especially for pure logic like scoring, classification, and parsing. Pure
functions with well-defined inputs are the cheapest, highest-value tests you can
write.

## Privacy guardrails

Somn's privacy claims (see [PRIVACY.md](PRIVACY.md)) are enforced by CI, not
just by memory. A pull request fails the build if:

- any Google Play Services artifact reappears on the release classpath
- the Auto Backup / device-transfer opt-out is dropped
- `INTERNET` starts being contributed by any module other than `core:data`

These rules exist because a transitive dependency can silently reintroduce any of
these. If your change seems to trip one, read the failing check's log — it
usually names the offending artifact or module.

## Reporting a security issue

Somn is a health app; treat security seriously. **Do not open a public issue for
a vulnerability.** Instead, open a GitHub security advisory or reach out to the
maintainers privately, and we'll handle disclosure responsibly.

## Code of conduct

Be kind. This is a small, friendly open-source project. Harassment, hate speech,
and personal attacks have no place here — in issues, PRs, or comments. Everyone
is here to make sleep tracking better, and disagreement should always stay
technical and respectful.

## License

By contributing you agree that your contributions are licensed under the
[GNU General Public License v3.0](LICENSE), the same license as the project.

---

*Thank you for helping make Somn better. Every contribution counts, no matter
how small.*
