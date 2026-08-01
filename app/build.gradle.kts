// Explicit import: inside a Kotlin build script `java` resolves to Gradle's java extension, so the
// fully-qualified java.util.Properties does not.
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
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

android {
    namespace = "dev.vic41148.somn.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.vic41148.somn"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    testImplementation(libs.junit)
}
