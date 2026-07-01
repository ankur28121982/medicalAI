# Medical Report AI

New standalone Android + FastAPI app copied from the Her Next Word build approach, but with medical-report functionality.

## What this app does

- Upload typed medical report PDF/image/camera photo.
- AI reads the report using OpenAI multimodal/file input.
- Shows **what AI read from the report before analysis**.
- Asks missing context questions like age, symptoms, allergies, medicines, pregnancy status.
- Gives low/medium/high concern areas in simple language.
- Gives doctor-prep questions and broad treatment directions to discuss with a doctor.
- Does **not** diagnose, prescribe, or recommend medicine doses.
- Saves history by device ID so future reports can be compared as a journal.

## Folder layout

```text
backend/                 FastAPI backend
mobile_android/          Native Android Java app
scripts/                 local run/build scripts
```

## Local backend run

```bash
cd backend
cp .env.example .env
# edit .env: OPENAI_API_KEY and APP_ACCESS_TOKEN
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

Or:

```bash
./scripts/run_backend_local.sh
```

## Android debug build

```bash
cd mobile_android
# ensure local.properties has sdk.dir=/home/ankur/Android/Sdk
./gradlew :app:assembleDebug --no-daemon --max-workers=1
```

Or:

```bash
./scripts/build_debug_apk.sh
```

Debug APK uses:

```text
http://10.0.2.2:8000
```

Release APK/AAB uses:

```text
https://medical-api.globalresearchforum.in
```

Change these in:

```text
mobile_android/app/build.gradle
```

## Production checklist before Play Store

1. Change package name if needed.
2. Change app name/icon.
3. Change `APP_ACCESS_TOKEN` in backend `.env` and Android `BuildConfig`.
4. Use HTTPS only in release.
5. Set `MEDICAL_ENCRYPTION_KEY` and `REQUIRE_ENCRYPTION=true` in production.
6. Add Privacy Policy, Data Safety, and Google Play Health Apps declaration.
7. Do not claim diagnosis, treatment, cure, emergency detection, or doctor replacement.
8. Add account/login before scaling beyond device-ID MVP.

## Generate encryption key

```bash
python3 -c "from cryptography.fernet import Fernet; print(Fernet.generate_key().decode())"
```

## API endpoints

```text
GET    /health
POST   /v1/report/upload
POST   /v1/report/analyze
GET    /v1/report/history?device_id=...
GET    /v1/report/{report_id}?device_id=...
DELETE /v1/report/{report_id}?device_id=...
```

## Safety position

Use this text in app/store listing:

> This app explains typed medical reports in simple language and helps prepare questions for a doctor. It does not provide diagnosis, medicine prescription, emergency medical service, or replacement for professional medical care.
