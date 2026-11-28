# F-Droid submission

The app is submitted to fdroiddata as a new app-inclusion merge request on branch
`Vic-41148/fdroiddata:dev.vic41148.somn`, superseding the closed
[!56893](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/56893). This directory
mirrors `metadata/dev.vic41148.somn.yml` so the build configuration is reviewable here
first.

## What gets built

F-Droid builds `assembleStoreRelease` from the tag commit. The `store` flavor:

- omits the self-updater and `REQUEST_INSTALL_PACKAGES`;
- has no Google Play Services, Firebase, ML Kit or analytics on the runtime classpath;
- ships the same universal APK as GitHub (`arm64-v8a` + `armeabi-v7a`), currently ~19 MB.

Once the app is accepted, the recipe's `Binaries:` and `AllowedAPKSigningKeys` mean
F-Droid verifies that its build reproduces the published APK byte-for-byte and
publishes it with the upstream signature. An F-Droid install can then update over a
GitHub-release install (and vice versa).

## Submitting

1. In the `dev.vic41148.somn` branch of the fork, copy only
   `fdroid/dev.vic41148.somn.yml` to `metadata/dev.vic41148.somn.yml`. Do not add
   `Summary`, `Description`, or a `metadata/dev.vic41148.somn/en-US/` directory:
   F-Droid reads the title, summary and full description from the upstream Fastlane
   metadata (`fastlane/metadata/android/en-US/`), and the MR template asks that
   upstream-provided text not be duplicated.
2. Run `fdroid readmeta`, `fdroid rewritemeta dev.vic41148.somn` (a no-op on the
   current file), and `fdroid lint dev.vic41148.somn`. (Checked locally with
   fdroidserver 2.4.5: lint reports nothing.)
3. Build and test it in the buildserver: `fdroid build dev.vic41148.somn --server`.
   The first build verifies Gradle 9.9.0 resolution and AGP's SDK download for
   `compileSdk 36`; both are known-good on the current buildserver.
4. Open a new MR with the repository's app-inclusion template. Do not reopen
   !56893. In the MR, explain `AutoUpdateMode: None` / `UpdateCheckMode: None` for
   v0.2.1 (see below) and the `scanignore` reason.

## Releasing a new version

The current submission targets v0.2.1 (versionCode 2001), the first release whose
version comes from `version.properties`. Keeping the version in a checked-in file
rather than `git describe` is what makes tarball and reproducible builds match.

v0.2.1 stays on `AutoUpdateMode: None` / `UpdateCheckMode: None`: `versionCode` is
computed in `app/build.gradle.kts` as `major * 1_000_000 + minor * 1_000 + patch`
and is not a literal that `fdroid checkupdates` can read out of `version.properties`.

### v0.2.2 and later

1. Add a literal `versionCode=` line to `version.properties`, and assert in
   `app/build.gradle.kts` that it matches the computed value (fail the build on a
   mismatch) so the two cannot drift.
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`. `2001.txt`
   was missing for v0.2.1; start with `2002.txt`.
3. Switch the fdroiddata recipe to auto update:

   ```yaml
   AutoUpdateMode: Version
   UpdateCheckMode: Tags ^v[0-9]+\.[0-9]+\.[0-9]+$
   UpdateCheckData: version.properties|versionCode=(\d+)|.|versionName=(.+)
   ```

   The `.` reuses `version.properties` as the source for `versionName`, so both
   values come from that one file.
4. Run `fdroid checkupdates dev.vic41148.somn` locally and confirm it finds the tag
   and the new `versionCode` before opening the MR.

After tagging, add a new `Builds` entry and bump `CurrentVersion` /
`CurrentVersionCode`:

- `versionName` → the tag without `v` (for example `0.2.2`);
- `versionCode` → the literal from `version.properties`.

Keep `commit:` on the immutable tag commit SHA.

Before announcing a release:

- Upload `Somn-store-v<version>.apk` to the GitHub release. The recipe's
  `Binaries:` URL points there, and `.github/workflows/release.yml` currently
  uploads only the standalone APK.
- Keep `SHA256SUMS.txt` covering the standalone APK only, so the in-app updater's
  checksum verification is unaffected.

## Notes for reviewers

- `scanignore` on `core/audio/build.gradle.kts` is for the SUSS "Google Play Core"
  signature entry matching `com.google.ai.edge.litert:litert(-api)?:2`. At
  `54f4c795` (the `v0.2.1` tag) the store runtime classpath resolves
  `com.google.ai.edge.litert:litert:2.1.5` as its only LiteRT artifact and contains
  no `com.google.android.play`, `play-services`, `firebase`, `crashlytics`,
  `sentry` or `mlkit` artifacts; `ai-delivery` is excluded in that file.
- `model/yamnet.tflite` is a source-tree test fixture (Apache-2.0); it is not
  packaged. If the binary scanner objects, `scandelete` it — the release build does
  not read it.
- Per-ABI splits are tracked in issue #91. If they land, F-Droid needs one build
  block per ABI with the higher version code on `arm64-v8a`; the universal APK
  remains valid in the meantime.
