plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.vic41148.somn.core.audio"
    compileSdk = 36

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
    androidResources {
        // yamnet.tflite must stay uncompressed in the APK — AssetManager.openFd() (used to
        // memory-map the model directly) throws for compressed asset entries.
        noCompress += "tflite"
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))

    implementation(libs.core.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.jtransforms)
    // LiteRT transitively pulls com.google.android.play:ai-delivery, which drags in
    // play-services-basement and Play asset-delivery — Google Play Services components that
    // are disqualifying under F-Droid's inclusion policy. ai-delivery only matters for models
    // downloaded at runtime via Play; yamnet.tflite ships in this module's assets and is loaded
    // straight into org.tensorflow.lite.Interpreter (YamnetAudioClassifier.kt:38), so nothing
    // on our code path touches it.
    implementation(libs.tensorflow.lite) {
        exclude(group = "com.google.android.play", module = "ai-delivery")
    }

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
