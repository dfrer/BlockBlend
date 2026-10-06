#!/usr/bin/env bash
# Build only; never installs or changes Minecraft profiles.
set -euo pipefail
cd "$(dirname "$0")"
if [[ -n "${JAVA_HOME:-}" ]]; then
  JAVA_EXE="$JAVA_HOME/bin/java"
else
  JAVA_EXE="java"
fi
if ! "$JAVA_EXE" -version 2>&1 | head -n 1 | grep -Eq 'version "21[.]'; then
  echo "Set JAVA_HOME to a Java 21 JDK." >&2
  exit 1
fi
if [[ $# -eq 0 ]]; then set -- build; fi
exec "$JAVA_EXE" -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain "$@" --no-daemon --console=plain
