#!/bin/sh
set -eu
: "${HMGAPI_SDK:?SDK missing}"
: "${HMGAPI_MOD_BUILD:?build dir missing}"
ANDROID_JAR=$(find "$ANDROID_HOME/platforms" -name android.jar | sort -V | tail -1)
D8=$(find "$ANDROID_HOME/build-tools" -name d8 -type f | sort -V | tail -1)
W=$(mktemp -d)
trap 'rm -rf "$W"' EXIT
mkdir -p "$W/classes" "$W/dex"
javac --release 8 -cp "$ANDROID_JAR:$HMGAPI_SDK/lib/hmgapi-client.jar" -d "$W/classes" HMG-IconDump/service-src/dev/hmg/icondump/IconDump.java
(cd "$W/classes" && jar xf "$HMGAPI_SDK/lib/hmgapi-client.jar")
"$D8" --release --min-api 19 --lib "$ANDROID_JAR" --output "$W/dex" $(find "$W/classes" -name '*.class')
(cd "$W/dex" && zip -q -X "$HMGAPI_MOD_BUILD/firmware-dump.jar" classes.dex)
cp "$HMGAPI_MOD_BUILD/firmware-dump.jar" HMG-IconDump/firmware-dump.jar
test -s HMG-IconDump/firmware-dump.jar
