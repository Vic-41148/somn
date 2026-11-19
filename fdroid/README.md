# F-Droid submission

The app is submitted to fdroiddata as MR
[!56893](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/56893) (source branch
`Vic-41148/fdroiddata:dev.vic41148.somn`). This directory mirrors
`metadata/dev.vic41148.somn.yml` so the build configuration is reviewable here first.

## What gets built

F-Droid builds `assembleStoreRelease` from the tag commit. The `store` flavor:

- omits the self-updater and `REQUEST_INSTALL_PACKAGES`;
- has no Google Play Services, Firebase, ML Kit or analytics on the runtime classpath;
- ships the same universal APK as GitHub (`arm64-v8a` + `armeabi-v7a`), currently ~19 MB.

F-Droid signs the result with its own key, so an F-Droid install cannot update over a
GitHub-release install (and vice versa). That is expected and worth stating in the MR.

## Submitting

1. In the `dev.vic41148.somn` branch of the fork, copy
   `fdroid/dev.vic41148.somn.yml` to `metadata/dev.vic41148.somn.yml`.
2. Run `fdroid readmeta`, `fdroid rewritemeta dev.vic41148.somn`, and
   `fdroid lint dev.vic41148.somn`. (Checked locally with fdroidserver 2.4.5:
   lint reports nothing, and `rewritemeta` only strips these comments.)
3. Build and test it in the buildserver: `fdroid build dev.vic41148.somn --server`.
   The first build verifies Gradle 9.9.0 resolution and AGP's SDK download for
   `compileSdk 36`; both are known-good on the current buildserver.
4. Update MR [!56893](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/56893),
   squashed to one commit titled `New App: dev.vic41148.somn`. Mention in the MR:
   no GMS/analytics, the self-updater is excluded from this flavor, and the YAMNet
   model is an **opt-in, checksum-pinned** download (the APK does not bundle it).

## Releasing a new version

The current submission targets v0.2.1 (versionCode 2001), the first release whose
version comes from `version.properties`. Keeping the version in a checked-in file
rather than `git describe` is what makes tarball and reproducible builds match.

`UpdateCheckMode` is `None` because the version lives in `version.properties`
rather than an `AndroidManifest.xml`/`build.gradle.kts` literal that
`fdroid checkupdates` can regex. After tagging a release, add a new `Builds` entry
to the fdroiddata metadata and bump `CurrentVersion` / `CurrentVersionCode`:

- `versionName` → the tag without `v` (for example `0.2.1`);
- `versionCode` → `major * 1_000_000 + minor * 1_000 + patch` (for example `2001`).

Keep `commit:` on the immutable tag commit SHA.

Before announcing a release:

- Upload `Somn-store-v<version>.apk` to the GitHub release. The recipe's
  `Binaries:` URL points there, and `.github/workflows/release.yml` currently
  uploads only the standalone APK.
- Keep `SHA256SUMS.txt` covering the standalone APK only, so the in-app updater's
  checksum verification is unaffected.

## Notes for reviewers

- `scanignore` on `core/audio/build.gradle.kts` is for the SUSS “Google Play Core”
  regex matching `com.google.ai.edge.litert:litert:2`. `ai-delivery` is excluded in
  that file, and the store runtime classpath has no `com.google.android.play`
  artifacts.
- `model/yamnet.tflite` is a source-tree test fixture (Apache-2.0); it is not
  packaged. If the binary scanner objects, `scandelete` it — the release build does
  not read it.
- Per-ABI splits are tracked in issue #91. If they land, F-Droid needs one build
  block per ABI with the higher version code on `arm64-v8a`; the universal APK
  remains valid in the meantime.
