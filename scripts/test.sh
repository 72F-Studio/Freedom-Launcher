#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
JAVA_BIN="${JAVA_HOME:-$HOME/.jdks/jbr-21.0.11}/bin"
mkdir -p build/rules-test
"$JAVA_BIN/javac" -d build/rules-test app/src/main/java/dev/seventwo/launcher/CategoryRules.java app/src/main/java/dev/seventwo/launcher/ContextRules.java app/src/main/java/dev/seventwo/launcher/Legibility.java tests/CategoryRulesTest.java tests/ContextRulesTest.java tests/LegibilityTest.java
"$JAVA_BIN/java" -cp build/rules-test CategoryRulesTest
"$JAVA_BIN/java" -cp build/rules-test dev.seventwo.launcher.ContextRulesTest
"$JAVA_BIN/java" -cp build/rules-test dev.seventwo.launcher.LegibilityTest
