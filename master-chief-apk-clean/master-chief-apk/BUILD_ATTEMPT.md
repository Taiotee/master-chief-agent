# Build attempt — what happened in this environment

Date: 26 Nov 2026. Environment: Linux VM (1.5 shared CPUs, 8 GB RAM total,
only ~500 MB free at the time), no Android SDK / JDK pre-installed.

## What was set up successfully

- JDK 17 (Eclipse Temurin 17.0.21) downloaded and verified (`java -version` works).
- Android SDK packages downloaded directly with curl and laid out by hand:
  `platforms;android-34`, `build-tools;34.0.0`, `platform-tools`
  (sdkmanager itself could not run — see below).
- Gradle 8.7 downloaded and verified (`gradle --version` works).
- Gradle wrapper files created in the project (`gradlew`, `gradlew.bat`,
  `gradle/wrapper/gradle-wrapper.jar` + properties). The wrapper jar was
  rebuilt with a `Main-Class` manifest and the wrapper successfully
  unpacked the local Gradle 8.7 distribution from a `file://` URL
  (properties restored to the standard `https://` URL afterwards).

## What failed — and why

**The Gradle daemon cannot start in this VM.** Six invocations, all the
same failure:

```
FAILURE: Build failed with an exception.
* What went wrong:
Could not receive a message from the daemon.
```

Tried: daemon and `--no-daemon`, heaps of 2 GB, 1 GB, 512 MB and 256 MB.
Every time the daemon process starts and is then stopped/killed by the
operating system within seconds (`gradle --status` shows the daemons as
STOPPED, "by user or operating system"). The VM has only ~500 MB free
RAM; the daemon JVM cannot stay alive in that. `gradle --version`
(which needs no daemon) works fine, so Gradle itself is installed
correctly — the machine simply cannot run a build.

Two environment quirks found along the way (both worked around, both
documented here so the next builder does not repeat them):
- `sdkmanager` fails with `java.util.NoSuchElementException` in
  `HttpURLConnection.doTunneling` — Java's HTTP client cannot speak
  through this VM's egress proxy, while curl can. SDK packages were
  therefore downloaded with curl from `dl.google.com` and unpacked by hand.
- The same proxy issue affects the wrapper's Gradle download; the
  wrapper was tested with a local `file://` distribution URL instead.
  The committed `gradle-wrapper.properties` still uses the standard
  `https://services.gradle.org` URL.

## Consequence

**No APK was produced in this environment, and none was installed on
Tayo's phone.** Nothing here is faked: there is no `app-debug.apk` in
the project, and the project does not claim one exists.

## How to build it for real

Open the project in **Android Studio** (Hedgehog or newer) on a machine
with at least 4 GB free RAM, let it sync Gradle, then
**Build > Build App Bundle(s) / APK(s) > Build APK(s)** — or run
`./scripts/build-apk.sh` on the command line (see `docs/BUILD.md`).
The debug APK lands in `app/build/outputs/apk/debug/app-debug.apk`;
install it with `adb install -r` over USB.

## What was verified without a full build

- All XML resources parse cleanly (validated with a Python XML parser).
- All 32 Kotlin files have balanced braces/parens (checked mechanically).
- The Compose compiler version was corrected to pair with Kotlin 1.9.24
  (1.5.14, not 1.7.8 — the latter needs Kotlin 2.0).
- A logic bug found in review was fixed: `PhotoSweeper`'s duplicate
  grouping now keeps the oldest copy and reports the real file size.
- No API keys, passwords or credentials are in the project. (Proxy
  credentials used during the build attempt were passed via environment
  variables only, never written into the project; temporary logs
  containing them were deleted.)
