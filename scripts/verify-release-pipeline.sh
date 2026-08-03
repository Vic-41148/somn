#!/bin/bash
cd /home/vic/Projects/somn

echo '=== 1) assembleRelease (the CI build step; unsigned APK, R8 minification) ==='
./gradlew assembleRelease --console=plain 2>&1 | grep -E 'error:|e: |FAILED|BUILD|warning: ' | tail -10

echo
echo '=== 2) alignment guardrail (debug-classpath check) ==='
./gradlew verifyComposeFoundationAlignment --console=plain 2>&1 | grep -E 'OK:|FAILED|BUILD' | head -3echo
 echo '=== 3) RESOLVED foundation on the RELEASE runtime classpath (authoritative) ==='
./gradlew -q :app:dependencyInsight --dependency androidx.compose.foundation:foundation \
  --configuration releaseRuntimeClasspath 2>/dev/null | grep -m1 -E 'foundation:.*[0-9]+\.' 

echo
echo '=== 4) release APK artifact ==='
ls -la app/build/outputs/apk/release/*.apk 2>/dev/null | head -4echo
 echo '=== 5) R8 minification clean (no missing-class/member linkage warnings) ==='
# R8 renames methods in release builds, so name-level dex checks are meaningless. The real
# guarantee is: (a) the resolved foundation on the release runtime classpath == what modules
# compile against (checked in step 3 + the guardrail), and (b) R8 minification completes with
# no missing-member warnings — R8 resolves every method reference against the actual runtime
# jars, so a compile-vs-runtime signature mismatch (the FlowRow NoSuchMethodError class of
# bug) surfaces here as a warning or error at build time.
./gradlew :app:assembleRelease --console=plain > /tmp/relbuild.log 2>&1
RC=$?
echo "  assembleRelease exit: $RC"
MISSING=$(grep -icE 'missing (class|method)|unresolved|cannot find|NoSuchMethod' /tmp/relbuild.log || true)
echo "  missing-member/unresolved warning count: $MISSING"
[ "$RC" -eq 0 ] && [ "$MISSING" = "0" ] && echo "OK: release pipeline clean" || echo "FAIL: see /tmp/relbuild.log"
