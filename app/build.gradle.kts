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

// Version derives from the git tag (v0.1.2 → code 1002, name "0.1.2") so the tag,
// the built APK, and what the self-updater compares can never disagree. Falls back
// to the last released values when git is unavailable (source tarball builds).
val releaseTag: String = runCatching {
    providers.exec { commandLine("git", "describe", "--tags", "--abbrev=0") }
        .standardOutput.asText.get().trim()
}.getOrDefault("v0.1.2")
val releaseParts: List<Int> = releaseTag.trimStart('v').split(".").map { it.toIntOrNull() ?: 0 }
val derivedVersionCode: Int =
    (releaseParts.getOrElse(0) { 0 }) * 1_000_000 +
        (releaseParts.getOrElse(1) { 0 }) * 1_000 +
        (releaseParts.getOrElse(2) { 0 })
val derivedVersionName: String = releaseParts.joinToString(".")

android {
    namespace = "dev.vic41148.somn.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.vic41148.somn"
        minSdk = 26
        targetSdk = 36
        versionCode = derivedVersionCode
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

    testImplementation(libs.junit)
}
