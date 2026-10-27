#!/usr/bin/env bash
# Creates the production signing key for 72F and an untracked keystore.properties.
# Run it yourself: keytool asks for passwords in your terminal; nothing is echoed or stored elsewhere.
# BACK UP the .jks and its passwords. Losing them means you can never ship an update to installs signed with it.
set -euo pipefail
cd "$(dirname "$0")/.."
KEYTOOL="${JAVA_HOME:-$HOME/.jdks/jbr-21.0.11}/bin/keytool"
STORE="${1:-./keystore/72f-release.jks}"
ALIAS=72f
[ -e keystore.properties ] && { echo "keystore.properties already exists; not overwriting."; exit 1; }
mkdir -p "$(dirname "$STORE")"
"$KEYTOOL" -genkeypair -v -keystore "$STORE" -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=72F Launcher"
read -r -s -p "Keystore password (again, for keystore.properties): " PASS; echo
umask 077
cat > keystore.properties <<PROPS
storeFile=$STORE
storePassword=$PASS
keyAlias=$ALIAS
keyPassword=$PASS
PROPS
echo "Wrote keystore.properties (untracked, mode 600). Release builds now sign with $STORE."
"$KEYTOOL" -list -v -keystore "$STORE" -alias "$ALIAS" -storepass "$PASS" | grep -E "SHA256:"
