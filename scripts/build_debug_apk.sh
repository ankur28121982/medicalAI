#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR/mobile_android"
./gradlew --stop || true
mkdir -p ~/.gradle
printf "org.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8\norg.gradle.workers.max=1\n" > ~/.gradle/gradle.properties
if [ ! -f local.properties ] && [ -d "$HOME/Android/Sdk" ]; then
  echo "sdk.dir=$HOME/Android/Sdk" > local.properties
fi
./gradlew :app:assembleDebug --no-daemon --max-workers=1
APK="$ROOT_DIR/mobile_android/app/build/outputs/apk/debug/app-debug.apk"
echo "DONE: $APK"
