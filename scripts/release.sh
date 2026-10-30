#!/usr/bin/env bash
# Builds the R8 release APK into dist/, signed and checksummed.
# With keystore.properties (scripts/make-release-key.sh) Gradle signs with the production key;
# otherwise the APK is signed with the development key that beta 1 through RC4 used.
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$HOME/.jdks/jbr-21.0.11}"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"; BT="$SDK/build-tools/37.0.0"; [ -x "$BT/apksigner" ] || BT="$SDK/build-tools/35.0.0"
VERSION=$(grep -oP 'versionName = "\K[^"]+' app/build.gradle.kts)
OUT="dist/Eigen-Launcher-$VERSION.apk"
./gradlew -q :app:assembleRelease
if [ -f keystore.properties ]; then
  cp app/build/outputs/apk/release/app-release.apk "$OUT"; echo "Signed with the production key."
else
  echo "WARNING: no keystore.properties; signing with the development (debug) key." >&2
  PATH="$JAVA_HOME/bin:$PATH" "$BT/apksigner" sign --ks "$HOME/.android/debug.keystore" --ks-pass pass:android \
    --ks-key-alias androiddebugkey --key-pass pass:android --out "$OUT" app/build/outputs/apk/release/app-release-unsigned.apk
fi
rm -f "$OUT.idsig"
PATH="$JAVA_HOME/bin:$PATH" "$BT/apksigner" verify --print-certs "$OUT" | grep "certificate SHA-256"
(cd dist && grep -v " $(basename "$OUT")\$" SHA256SUMS > SHA256SUMS.tmp || true; sha256sum "$(basename "$OUT")" >> SHA256SUMS.tmp; mv SHA256SUMS.tmp SHA256SUMS)
echo "$OUT"
