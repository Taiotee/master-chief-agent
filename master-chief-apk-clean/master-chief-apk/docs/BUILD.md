# Building the APK

## Option A — Android Studio (recommended)

1. Install Android Studio (Hedgehog or newer) and open this folder.
2. Let it sync Gradle and download SDK 34 + build-tools 34.0.0.
3. **Build > Build App Bundle(s) / APK(s) > Build APK(s)**.
4. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
   (applicationId `com.masterchief.agent.debug`).
5. To install on the OPPO A74 5G: enable Developer options > USB
   debugging, connect by USB, press Run in Android Studio — or
   `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Option B — command line

```bash
# JDK 17 required. Check: java -version
# Android SDK required. Set it in local.properties:
cp local.properties.example local.properties
# edit local.properties: sdk.dir=/path/to/Android/Sdk

./scripts/build-apk.sh        # builds debug APK
./gradlew assembleRelease     # release APK (needs signing, see below)
```

`scripts/build-apk.sh` downloads the Gradle wrapper on first run and
then runs `./gradlew assembleDebug`.

## Release signing (sideloaded release, not Play Store)

A release APK must be signed with a key **you** control:

```bash
keytool -genkeypair -v -keystore master-chief-release.keystore \
  -alias chief -keyalg RSA -keysize 2048 -validity 10000
```

Then add to `~/.gradle/gradle.properties` (never in this repo):

```properties
CHIEF_KEYSTORE=/path/to/master-chief-release.keystore
CHIEF_KEY_ALIAS=chief
CHIEF_KEY_PASSWORD=...
CHIEF_STORE_PASSWORD=...
```

And add a `signingConfigs` block to `app/build.gradle.kts` (left out of
v1 on purpose — signing config with passwords must never be committed).

**Do not publish to the Play Store** without a privacy policy, a review
of the notification-listener / overlay permissions, and a signed key
you control. Play restricts those permissions and will reject or
remove apps that use them without a clear, user-facing purpose.

## Troubleshooting

- `SDK location not found` → create `local.properties` (see above).
- Gradle download slow → first sync downloads ~200 MB; be patient.
- Build fails on `compileSdk 34` → install SDK 34 in SDK Manager.
- `minSdk 29` is deliberate: the APK also runs on older Android phones,
  not just the A74 5G (Android 13).
- Out of memory on a small machine → `org.gradle.jvmargs=-Xmx2g` is
  already set in `gradle.properties`; raise it if you have more RAM.
