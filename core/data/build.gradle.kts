plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.vic41148.somn.core.data"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    sourceSets {
        getByName("test") {
            assets.srcDirs("$projectDir/schemas")
        }
    }

    flavorDimensions += "channel"
    productFlavors {
        create("standalone") {
            dimension = "channel"
        }
        create("store") {
            dimension = "channel"
        }
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:health"))

    implementation(libs.core.ktx)
    implementation(libs.coroutines.android)
    // api: HealthConnectRepository exposes PermissionController's ActivityResultContract type
    // to feature/settings, which only depends on core:data (not core:health directly).
    api(libs.health.connect)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // DocumentFile
    implementation(libs.documentfile)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.ext.compiler)

    // WorkManager
    implementation(libs.work.runtime)
    implementation(libs.hilt.work)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)

    // Backup/restore touches real SQLite, the WAL, and the Keystore — none of which Robolectric
    // reproduces faithfully enough to trust, so those run on-device.
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.coroutines.test)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
