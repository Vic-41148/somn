plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.vic41148.somn.core.health"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
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
        unitTests.all {
            // HealthConnectManifestPermissionsTest reads the module manifest directly.
            it.systemProperty("somn.health.manifest", file("src/main/AndroidManifest.xml").absolutePath)
        }
    }
}

dependencies {
    implementation(project(":core:domain"))

    implementation(libs.core.ktx)
    implementation(libs.health.connect)
    implementation(libs.coroutines.android)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
}
