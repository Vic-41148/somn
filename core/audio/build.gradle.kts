plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.vic41148.somn.core.audio"
    compileSdk = 35

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    
    implementation(libs.core.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.jtransforms)
    
    testImplementation(libs.junit)
}
