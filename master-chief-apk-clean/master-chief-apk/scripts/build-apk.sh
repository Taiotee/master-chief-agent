#!/usr/bin/env bash
# Build the Master Chief debug APK.
# Requires: JDK 17, Android SDK (set sdk.dir in local.properties).
set -euo pipefail
cd "$(dirname "$0")/.."
if [ ! -f local.properties ]; then
  echo "local.properties not found. Copy local.properties.example and set sdk.dir:"
  echo "  cp local.properties.example local.properties"
  exit 1
fi
if [ ! -x ./gradlew ]; then
  echo "gradlew not found — downloading the Gradle wrapper jar is a manual step:"
  echo "  gradle wrapper --gradle-version 8.7   (once, on a machine with Gradle)"
  echo "  or open the project in Android Studio, which generates it."
  exit 1
fi
./gradlew assembleDebug
echo "APK: app/build/outputs/apk/debug/app-debug.apk"
echo "Install: adb install -r app/build/outputs/apk/debug/app-debug.apk"
