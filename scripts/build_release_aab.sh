#!/usr/bin/env bash
set -euo pipefail
: "${MEDREPORT_KEYSTORE_FILE:?Set MEDREPORT_KEYSTORE_FILE}"
: "${MEDREPORT_KEYSTORE_PASSWORD:?Set MEDREPORT_KEYSTORE_PASSWORD}"
: "${MEDREPORT_KEY_ALIAS:?Set MEDREPORT_KEY_ALIAS}"
: "${MEDREPORT_KEY_PASSWORD:?Set MEDREPORT_KEY_PASSWORD}"
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR/mobile_android"
./gradlew --stop || true
./gradlew :app:bundleRelease --no-daemon --max-workers=1
AAB="$ROOT_DIR/mobile_android/app/build/outputs/bundle/release/app-release.aab"
echo "DONE: $AAB"
