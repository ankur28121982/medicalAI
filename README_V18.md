# Medical Report AI V18

V18 is based on V17 and keeps the audit/logging and safety flow.

## What changed in V18

- Removed the top result block named `Aaj ki savdhani / Care priority` from the final report screen.
- The final report now starts directly with `Report summary / रिपोर्ट की मुख्य बात`.
- Removed saved-report comparison from the main patient screen.
- Every new AI run is now independent for better accuracy.
- Android sends `continue_from_previous=false` and `previous_report_id=""` for every analysis.
- Backend also ignores any previous report ID even if an old app sends it.
- Saved reports remain available in `My account` for viewing and doctor discussion only.
- Old reports are not used as AI context.
- Audit event for analysis now records `independent_run_no_history=true` and `same_patient_previous_report_used=false`.
- BK/Rajyoga waiting slider, calm content JSON, YouTube random picker, PDF download/share, credits, saved reports, and existing audit endpoints are preserved.

## Safety rules kept

- No diagnosis.
- No medicine name or dosage recommendation.
- No direct treatment instruction.
- Doctor-discussion support only.
- AI can make mistakes disclaimer remains visible.
- Calm medical-help wording remains in lower sections where relevant, but panic wording and direct emergency-number wording are avoided.

## Important production warning

This is still a debug/test build until Android Gradle build, real phone testing, PDF upload, typed-only flow, PDF download/share, backend reachability, and audit DB checks pass.

Real production users need HTTPS cloud backend, privacy policy, Play Store health declaration, and secure Google Play Billing backend verification.

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
APK_DEST="$OUT_DIR/medical-report-ai-v18-debug-$(date +%Y%m%d_%H%M%S).apk"
mkdir -p "$OUT_DIR"
cp "$APK_SRC" "$APK_DEST"
echo "$APK_DEST"
explorer.exe "$(wslpath -w "$OUT_DIR")"
```
