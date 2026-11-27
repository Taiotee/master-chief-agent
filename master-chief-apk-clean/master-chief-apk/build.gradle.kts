// Top-level build file — Master Chief standalone Android app.
// Gradle 8.7 + Android Gradle Plugin 8.5.2 + Kotlin 1.9.24.
// Target device: OPPO A74 5G (CPH2197), Snapdragon 480 5G, 6 GB RAM, Android 13.
// Keep minSdk 29 so the APK also runs on older devices; targetSdk 34.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "1.9.24" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24" apply false
}
