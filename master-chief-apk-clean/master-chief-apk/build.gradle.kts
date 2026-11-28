// Top-level build file — Master Chief standalone Android app.
// Gradle 8.7 + Android Gradle Plugin 8.5.2 + Kotlin 2.0.21.
// Target device: OPPO A74 5G (CPH2197), Snapdragon 480 5G, 6 GB RAM, Android 13.
// Keep minSdk 29 so the APK also runs on older devices; targetSdk 34.
// Kotlin 2.0.21 is required: kotlinx.serialization 1.7.1 needs Kotlin >=2.0,
// and org.jetbrains.kotlin.plugin.compose only exists for Kotlin 2.0+.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
}
