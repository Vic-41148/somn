// Explicit import: inside a Kotlin build script `java` resolves to Gradle's java extension, so the
// fully-qualified java.util.Properties does not.
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    // CycloneDX SBOM for the release artifact (FOSS, no GMS anywhere near it).
    alias(libs.plugins.cyclonedx)
    // Generates R.raw.aboutlibraries from dependency metadata for the license screen.
    alias(libs.plugins.aboutlibraries.plugin)
}

// Release signing credentials live in keystore.properties (gitignored) so the keystore password
// never enters git history. Absent that file the release build still assembles — it just falls
// back to being unsigned, rather than failing the whole configuration phase for debug builds too.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

// Version lives in version.properties, not `git describe`: F-Droid's buildserver and its
// reproducible-build pipeline build from a source tarball with no .git, where a git-derived
// version silently falls back to a stale value. The Release workflow fails if the pushed tag
// disagrees with this file, so the tag, the APK and the self-updater still cannot diverge.
// versionCode is declared in the same file because F-Droid's UpdateCheckData regex
// reads it at the release tag; the build fails if it disagrees with the value
// derived from versionName (0.2.0 -> 2000), so the two cannot drift.
val versionPropertiesFile = rootProject.file("version.properties")
require(versionPropertiesFile.exists()) {
    "version.properties is missing; it is the single source of truth for the app version"
}
val versionProperties = Properties().apply {
    versionPropertiesFile.inputStream().use { load(it) }
}
val derivedVersionName: String = versionProperties.getProperty("versionName")?.trim()
    ?: error("version.properties must define versionName")
val releaseParts: List<Int> = derivedVersionName.split(".").map { part ->
    part.toIntOrNull() ?: error("versionName '$derivedVersionName' must be numeric major.minor.patch")
}
require(releaseParts.size == 3) {
    "versionName '$derivedVersionName' must be major.minor.patch"
}
val derivedVersionCode: Int =
    releaseParts[0] * 1_000_000 +
        releaseParts[1] * 1_000 +
        releaseParts[2]
val declaredVersionCode: Int = versionProperties.getProperty("versionCode")?.trim()?.toIntOrNull()
    ?: error("version.properties must define a numeric versionCode")
require(declaredVersionCode == derivedVersionCode) {
    "version.properties versionCode=$declaredVersionCode does not match the value derived from " +
        "versionName '$derivedVersionName' ($derivedVersionCode)"
}

// Nightly signing material comes from the environment only (CI secrets). There is deliberately no
// properties-file fallback and no access to keystore.properties: the nightly key must never be the
// release key. Absent these variables the nightly build type assembles unsigned and CI refuses it.
val nightlyKeystorePath: String? = System.getenv("NIGHTLY_KEYSTORE_PATH")

android {
    namespace = "dev.vic41148.somn.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.vic41148.somn"
        minSdk = 26
        targetSdk = 36
        versionCode = declaredVersionCode
        versionName = derivedVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // Phones only: x86/x86_64 exist for emulators and cost ~14 MB of .so weight
            // (TF Lite runtime + QR scanner ship per-ABI natives). Physical devices are
            // arm64 (or armv7), so the store channel drops the emulator ABIs.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
        if (nightlyKeystorePath != null) {
            create("nightly") {
                storeFile = file(nightlyKeystorePath)
                storePassword = System.getenv("NIGHTLY_STORE_PASS")
                keyAlias = System.getenv("NIGHTLY_KEY_ALIAS")
                keyPassword = System.getenv("NIGHTLY_KEY_PASS")
            }
        }
    }

    // AGP encrypts the app's dependency tree into an extra APK signing block for Play Console
    // SDK alerts. F-Droid's scanner rejects any signing block it cannot verify, so the release
    // APK must not carry it. Google Play accepts APKs/AABs without it.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
        // Rolling preview channel: same code and R8 config as release, but a separate app
        // (dev.vic41148.somn.nightly) with its own key, so it installs next to a stable build
        // and can never be mistaken for, or update, one. Non-debuggable.
        create("nightly") {
            initWith(getByName("release"))
            // Library modules only define debug/release; resolve them to release.
            matchingFallbacks += listOf("release")
            applicationIdSuffix = ".nightly"
            versionNameSuffix = "-nightly"
            // Overrides the release config copied by initWith. Null when the nightly env vars are
            // absent; AGP then emits app-store-nightly-unsigned.apk, which the workflow's verify
            // step rejects by name.
            signingConfig = signingConfigs.findByName("nightly")
        }
    }

    flavorDimensions += "channel"
    productFlavors {
        create("standalone") {
            dimension = "channel"
            // Debug/development builds default to the standalone channel with the full
            // in-app updater (GitHub Releases + self-hosted repo) wired in.
        }
        create("store") {
            dimension = "channel"
            // F-Droid / IzzyOnDroid / Accrescent channel with the updater byte excluded.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            // Must stay false for 16KB-page devices: legacy packaging compresses the .so files and
            // the loader extracts them at install time, which defeats the page-aligned mmap the
            // 16KB ABI requires. False keeps them uncompressed and aligned inside the APK.
            useLegacyPackaging = false
            // YAMNet runs on the CPU Interpreter (YamnetAudioClassifier) — the OpenCL GPU
            // delegate .so ships inside the LiteRT AAR unused (~6 MB across ABIs).
            excludes += "**/libLiteRtClGlAccelerator.so"
        }
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:domain"))
    implementation(project(":core:audio"))
    implementation(project(":core:ui"))
    implementation(project(":feature:tracking"))
    implementation(project(":feature:alarm"))
    implementation(project(":feature:analytics"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:habits"))
    implementation(project(":core:notifications"))
    implementation(project(":feature:winddown"))

    // WorkManager — HiltWorkerFactory wiring for @HiltWorker workers (NasSyncWorker, WeeklyReportGenerator)
    implementation(libs.work.runtime)
    implementation(libs.hilt.work)
    ksp(libs.hilt.ext.compiler)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.graphics.shapes)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Open-source licenses screen (FOSS AboutLibraries, not GMS oss-licenses-plugin).
    implementation(libs.aboutlibraries)

    // Opt-in app lock: biometric or device credential at cold start.
    implementation(libs.biometric)

    // LeakCanary: memory-leak detection, debug builds only. Auto-installs, nothing ships in release.
    debugImplementation(libs.leakcanary)

    testImplementation(libs.junit)
}
