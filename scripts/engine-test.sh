#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
ADB="${ADB:-/home/pk/Android/Sdk/platform-tools/adb}"
SERIAL="${ANDROID_SERIAL:-emulator-5554}"
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :iconpack-test:assembleDebug
"$ADB" -s "$SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" -s "$SERIAL" install -r iconpack-test/build/outputs/apk/debug/iconpack-test-debug.apk
"$ADB" -s "$SERIAL" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
"$ADB" -s "$SERIAL" shell am instrument -w com.studio72f.eigen.test/com.studio72f.eigen.EngineChecks
