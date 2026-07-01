#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$HOME/medical_report_ai_v1"
ANDROID_DIR="$PROJECT_ROOT/mobile_android"

echo "Checking Android project..."
if [ ! -d "$ANDROID_DIR" ]; then
  echo "ERROR: Android folder not found: $ANDROID_DIR"
  exit 1
fi

echo "Creating/updating gradle.properties..."
cat > "$ANDROID_DIR/gradle.properties" <<'EOF'
# AndroidX is required because the app uses AndroidX dependencies.
android.useAndroidX=true

# Keep resources stable and smaller.
android.nonTransitiveRClass=true

# Safer memory settings for WSL builds.
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8

# Avoid daemon issues in WSL.
org.gradle.daemon=false

# Keep build simple and stable.
org.gradle.parallel=false
org.gradle.configureondemand=false
EOF

echo "Stopping Gradle daemons..."
cd "$ANDROID_DIR"
./gradlew --stop || true

echo "Building debug APK..."
cd "$PROJECT_ROOT"
export MEDREPORT_APP_TOKEN="$(cat "$HOME/medical_report_ai_token.txt")"
./scripts/build_debug_apk.sh

echo ""
echo "DONE."
echo "APK should be here:"
echo "$PROJECT_ROOT/mobile_android/app/build/outputs/apk/debug/app-debug.apk"
