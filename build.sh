#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
: "${PULSE_ANDROID_JAR:?Set to Android SDK API 36 android.jar}"
: "${PULSE_ANDROID_TOOLS:?Set to Android SDK build-tools 36 directory}"
: "${PULSE_ECJ_JAR:?Set to Eclipse ECJ 3.39.0 jar}"
: "${PULSE_SIGNING_STORE:?Set to a private Android beta signing keystore}"
mkdir -p build/{flat,gen,classes,dex}
"${PULSE_ANDROID_TOOLS}/aapt2" compile --dir src/main/res -o build/flat
"${PULSE_ANDROID_TOOLS}/aapt2" link -I "$PULSE_ANDROID_JAR" --manifest src/main/AndroidManifest.xml \
    --java build/gen -o build/resources.apk build/flat/*.flat
mapfile -t sources < <(find src/main/java build/gen -name '*.java' -type f)
mapfile -t dependencies < <(find vendor -maxdepth 1 -name "*.jar" -type f | sort)
dependency_path=$(IFS=:; echo "${dependencies[*]}")
java -jar "$PULSE_ECJ_JAR" -source 8 -target 8 -encoding UTF-8 -proc:none \
    -classpath "$dependency_path" -bootclasspath "$PULSE_ANDROID_JAR:${PULSE_ANDROID_TOOLS}/core-lambda-stubs.jar" -d build/classes "${sources[@]}"
(cd build/classes && zip -q -r ../classes.jar .)
"${PULSE_ANDROID_TOOLS}/d8" --min-api 26 --lib "$PULSE_ANDROID_JAR" --output build/dex build/classes.jar "${dependencies[@]}"
cp build/resources.apk build/unsigned.apk
mkdir -p build/java-resources
unzip -oq vendor/rhino.jar "org/mozilla/javascript/resources/*.properties" -d build/java-resources
(cd build/java-resources && zip -q -r ../unsigned.apk org)
(cd build/dex && zip -q -0 ../unsigned.apk classes*.dex)
"${PULSE_ANDROID_TOOLS}/zipalign" -f 4 build/unsigned.apk build/aligned.apk
"${PULSE_ANDROID_TOOLS}/apksigner" sign --ks "$PULSE_SIGNING_STORE" --ks-key-alias pulse-beta \
    --ks-pass env:PULSE_SIGNING_PASSWORD --out build/Pulse-Android-0.7-beta.apk build/aligned.apk
"${PULSE_ANDROID_TOOLS}/apksigner" verify --verbose build/Pulse-Android-0.7-beta.apk
