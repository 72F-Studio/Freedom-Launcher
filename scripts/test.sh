#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
JAVA_BIN="${JAVA_HOME:-/opt/android-studio/jbr}/bin"
mkdir -p build/rules-test
"$JAVA_BIN/javac" -d build/rules-test app/src/main/java/dev/seventwo/launcher/CategoryRules.java app/src/main/java/dev/seventwo/launcher/ContextRules.java tests/CategoryRulesTest.java tests/ContextRulesTest.java
"$JAVA_BIN/java" -cp build/rules-test CategoryRulesTest
"$JAVA_BIN/java" -cp build/rules-test dev.seventwo.launcher.ContextRulesTest
