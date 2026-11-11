# F-Droid submission

The app is F-Droid-eligible but not submitted yet (issue #8). This directory holds
the recipe to copy into [fdroiddata](https://gitlab.com/fdroid/fdroiddata) so the
build configuration is reviewable here first.

## What gets built

F-Droid builds `assembleStoreRelease` from the tag commit. The `store` flavor:

- omits the self-updater and `REQUEST_INSTALL_PACKAGES`;
- has no Google Play Services, Firebase, ML Kit or analytics on the runtime classpath;
- ships the same universal APK as GitHub (`arm64-v8a` + `armeabi-v7a`), currently ~19 MB.

F-Droid signs the result with its own key, so an F-Droid install cannot update over a
GitHub-release install (and vice versa). That is expected and worth stating in the MR.

## Submitting

1. Fork `fdroiddata` and copy `fdroid/dev.vic41148.somn.yml` to
   `metadata/dev.vic41148.somn.yml`.
2. Run `fdroid readmeta`, `fdroid rewritemeta dev.vic41148.somn`, and
   `fdroid lint dev.vic41148.somn`. (Checked locally with fdroidserver 2.4.5:
   lint reports nothing, and `rewritemeta` only strips these comments.)
3. Build and test it in the buildserver: `fdroid build dev.vic41148.somn --server`.
   The first build verifies Gradle 9.8.2 resolution and AGP's SDK download for
   `compileSdk 36`; both are known-good on the current buildserver.
4. Open a merge request against `fdroiddata`. Mention in the MR: no GMS/analytics,
   the self-updater is excluded from this flavor, and the YAMNet model is an
   **opt-in, checksum-pinned** download (the APK does not bundle it).

## Releasing a new version

The first submission targets v0.2.0, which predates `version.properties`; the
buildserver's git clone still has the tag, so the build produces 2000/0.2.0.
Replace that `Builds` entry with the first release that contains
`version.properties` (v0.2.1 or later) before relying on reproducible builds.

`UpdateCheckMode` is `None` because the version lives in `version.properties`
rather than an `AndroidManifest.xml`/`build.gradle.kts` literal that
`fdroid checkupdates` can regex. After tagging a release, add a new `Builds` entry
to the fdroiddata metadata and bump `CurrentVersion` / `CurrentVersionCode`:

- `versionName` → the tag without `v` (for example `0.2.1`);
- `versionCode` → `major * 1_000_000 + minor * 1_000 + patch` (for example `2001`).

Keep `commit:` on the immutable tag commit SHA.

## Notes for reviewers

- `model/yamnet.tflite` is a source-tree test fixture (Apache-2.0); it is not
  packaged. If the binary scanner objects, `scandelete` it — the release build does
  not read it.
- Per-ABI splits are tracked in issue #91. If they land, F-Droid needs one build
  block per ABI with the higher version code on `arm64-v8a`; the universal APK
  remains valid in the meantime.
