#!/usr/bin/env bash
set -euo pipefail

PROJECT="$HOME/medical_report_ai_v1"
JAVA_FILE="$PROJECT/mobile_android/app/src/main/java/com/globalresearchforum/medreport/MainActivity.java"

echo "Backing up MainActivity.java..."
cp "$JAVA_FILE" "$JAVA_FILE.before_v12_semicolon_fix_$(date +%Y%m%d_%H%M%S)"

echo "Fixing missing semicolon..."
python3 - "$JAVA_FILE" <<'PY'
from pathlib import Path
import re
import sys

path = Path(sys.argv[1])
text = path.read_text()

text2 = re.sub(
    r'(analyzeButton\.setText\("Answer questions"\))\s*;',
    r'\1;',
    text
)

text2 = re.sub(
    r'(analyzeButton\.setText\("Answer questions"\))\s*\n',
    r'\1;\n',
    text2
)

if text2 == text:
    print("No change made. Showing matching lines:")
    for i, line in enumerate(text.splitlines(), 1):
        if "Answer questions" in line:
            print(i, line)
else:
    path.write_text(text2)
    print("Fixed semicolon.")
PY

echo "Checking fixed line..."
grep -n "Answer questions" "$JAVA_FILE" || true

echo "Building debug APK..."
cd "$PROJECT"
export MEDREPORT_APP_TOKEN="$(cat "$HOME/medical_report_ai_token.txt")"
./scripts/build_debug_apk.sh

echo "Copying APK to D drive..."
OUT_DIR="/mnt/d/00 AI Projects/06 Medical_app/apk_output"
APK_SRC="$PROJECT/mobile_android/app/build/outputs/apk/debug/app-debug.apk"
APK_DEST="$OUT_DIR/medical-report-ai-v12-debug-fixed-$(date +%Y%m%d_%H%M%S).apk"

mkdir -p "$OUT_DIR"
cp "$APK_SRC" "$APK_DEST"

echo ""
echo "DONE."
echo "APK copied to:"
echo "$APK_DEST"

explorer.exe "$(wslpath -w "$OUT_DIR")"
