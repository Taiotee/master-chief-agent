plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.masterchief.agent"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.masterchief.agent"
        minSdk = 29
        // OPPO A74 5G ships Android 11, updated to Android 13. targetSdk 34 is
        // the Play requirement for new apps in 2024+; sideloaded debug builds
        // work the same.
        targetSdk = 34
        versionCode = 3
        versionName = "1.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Debug APK: no minification, so a first build on a slow machine
            // (and the 6 GB RAM target phone) stays fast to iterate on.
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Settings / brain-mode persistence (lightweight; no Room needed).
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Daily 06:30 Lagos briefing + time-of-day protocols, survives reboot.
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Gemini API calls. OkHttp is the only network dependency; the Gemini
    // REST call itself is plain JSON, so no heavy Google AI SDK is required.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

    // On-device speech: Android SpeechRecognizer + TextToSpeech (no extra dep).
    // Optional local LLM: MediaPipe LLM Inference (tasks-genai). Kept OUT of
    // the default build on purpose — see brain/LocalLlm.kt. Uncomment only
    // when a ~1–2 GB quantised .task model has been placed on the device;
    // the A74 5G (6 GB RAM) can only run small models (see README § Local AI).
    // implementation("com.google.mediapipe:tasks-genai:0.10.14")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
