# Medical Report AI V17

V17 is a standalone Android + FastAPI codebase for Medical Report AI.

## What changed in V17

- Waiting screen now shows a Rajyoga/BK-style calm slider every 5 seconds.
- Added `mobile_android/app/src/main/assets/calm_content.json` for calm messages and YouTube links.
- App randomly picks enabled YouTube videos from JSON for waiting/result screens.
- Placeholder YouTube links are skipped safely.
- Result screen starts constructively as overall report summary, not panic wording.
- Removed the family-summary wording.
- Removed panic-style direct phone-number wording from user-facing result.
- Urgent-care wording is now calmer: “contact doctor / nearby medical service” style.
- Same-patient comparison still only uses the exact old report selected by the user.
- After AI questions are answered, app goes directly to the final report.
- Scroll position save/restore is preserved.
- PDF download/share is preserved.
- Backend audit log coverage improved for upload, analyze, unclear-analysis skip, history, read, delete, client events, and credit events.
- Added `/v1/audit/credit` endpoint. This is audit only, not payment verification.

## Safety rules kept

- No diagnosis.
- No medicine name or dose recommendation.
- No treatment instruction.
- Only doctor-discussion support.
- AI can make mistakes disclaimer remains visible.
- Upload is optional; typed symptoms/report values work.

## Important production warning

This code is closer to production structure, but final production release still needs:

- HTTPS cloud backend.
- Real Google Play Billing integration.
- Backend purchase verification.
- Privacy policy and Play Store health app declaration.
- Real phone testing with PDF upload, typed-only flow, history, PDF download, and audit log checks.

## Add real YouTube links

Edit:

```text
mobile_android/app/src/main/assets/calm_content.json
```

Replace:

```text
PASTE_YOUTUBE_VIDEO_ID_HERE
```

with real YouTube video IDs/URLs. Placeholder videos are intentionally hidden.

## Build debug APK

```bash
cd ~/medical_report_ai_v1
export MEDREPORT_APP_TOKEN="$(cat ~/medical_report_ai_token.txt)"
./scripts/build_debug_apk.sh
```

## Copy debug APK to D drive

```bash
OUT_DIR="/mnt/d/00 AI Projects/06 Medical_app/apk_output"
APK_SRC="$HOME/medical_report_ai_v1/mobile_android/app/build/outputs/apk/debug/app-debug.apk"
APK_DEST="$OUT_DIR/medical-report-ai-v17-debug-$(date +%Y%m%d_%H%M%S).apk"
mkdir -p "$OUT_DIR"
cp "$APK_SRC" "$APK_DEST"
echo "$APK_DEST"
explorer.exe "$(wslpath -w "$OUT_DIR")"
```
