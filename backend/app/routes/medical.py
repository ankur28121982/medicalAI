import hashlib
import json
import time
import uuid
from typing import Any

from fastapi import APIRouter, Body, Depends, File, Form, HTTPException, Request, UploadFile, status

from app.core.config import get_settings
from app.core.crypto import encrypt_bytes
from app.core.rate_limit import check_rate_limit
from app.core.security import require_app_token
from app.db import audit_event, create_report, delete_report, get_report, list_reports, update_analysis, update_extraction, upsert_patient
from app.schemas import AnalyzeRequest, AnalyzeResponse, DeleteResponse, HistoryResponse, UploadResponse
from app.services.openai_medical_service import analyze_report, extract_report_files

router = APIRouter()

ALLOWED_TYPES = {"application/pdf", "image/jpeg", "image/png", "image/webp", "text/plain"}
MAX_FILES_PER_REPORT = 6


def _latency_ms(start: float) -> int:
    return int((time.perf_counter() - start) * 1000)


def _http_error_type(exc: HTTPException) -> str:
    return f"http_{exc.status_code}"


def _parse_profile(raw: str) -> dict[str, Any]:
    if not raw:
        return {}
    try:
        value = json.loads(raw)
        return value if isinstance(value, dict) else {}
    except Exception:
        return {}


def _safe_filename(name: str | None) -> str:
    name = (name or "medical_report").replace("\\", "_").replace("/", "_").strip()
    return name[:120] or "medical_report"


async def _read_upload(file: UploadFile) -> bytes:
    settings = get_settings()
    data = await file.read()
    max_bytes = settings.max_upload_mb * 1024 * 1024
    if len(data) > max_bytes:
        raise HTTPException(status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE, detail=f"File too large. Max allowed per file is {settings.max_upload_mb} MB.")
    if len(data) < 64:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Uploaded file looks empty or invalid.")
    return data


def _validate_upload(file: UploadFile) -> str:
    content_type = (file.content_type or "application/octet-stream").lower().strip()
    filename = (file.filename or "").lower()
    if filename.endswith(".pdf"):
        content_type = "application/pdf"
    elif filename.endswith(".jpg") or filename.endswith(".jpeg"):
        content_type = "image/jpeg"
    elif filename.endswith(".png"):
        content_type = "image/png"
    elif filename.endswith(".webp"):
        content_type = "image/webp"
    elif filename.endswith(".txt"):
        content_type = "text/plain"
    if content_type not in ALLOWED_TYPES:
        raise HTTPException(status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE, detail="Only PDF, JPG, PNG, WEBP, and typed text are supported.")
    return content_type


def _unclear_analysis(req: AnalyzeRequest, extracted: dict[str, Any]) -> dict[str, Any]:
    return {
        "title": "Clear report needed",
        "detected_language": req.language,
        "safety_note": "This report is handwritten, unclear, or not safely readable. For safety, upload a typed/printed clear report.",
        "final_conclusion_simple": "The report is not clear enough to safely explain. Please upload a clear typed or printed report.",
        "what_to_tell_doctor_simple": ["Please review the original report because AI could not read it safely."],
        "ai_safe_next_options_to_discuss": ["Upload a clearer report, repeat/confirm the test if the doctor advises, or take the original report to the doctor."],
        "seriousness_in_simple_words": "The main concern is that the report could be misread. A wrong reading can lead to wrong action.",
        "red_amber_green_boxes": [{"color": "Amber", "title": "Report not readable", "simple_message": "The report is unclear, handwritten, or not safely machine-readable.", "what_to_do": "Upload a clear typed/printed report or show the original to a doctor.", "why_this_color": "AI cannot safely verify the values."}],
        "emergency_escalation_needed": False,
        "emergency_escalation_reason": "No urgent signal was safely read from the unclear report. Use current symptoms to decide whether medical help is needed faster.",
        "action_checklist": ["Upload a clear typed or printed report", "Carry the original report to the doctor", "Do not start or stop medicines based on this unclear report"],
        "plain_language_glossary": [],
        "when_it_can_become_serious": ["Contact a doctor or nearby medical service now if there is chest pain, severe breathlessness, fainting, severe bleeding, stroke-like symptoms, severe allergy, or the patient looks very unwell."],
        "simple_next_steps_now": ["Upload a clearer report", "Show the original report to a doctor", "Keep taking only doctor-prescribed medicines"],
        "home_support_simple": ["Rest safely while arranging doctor review. Do not use home remedies as a replacement for medical review."],
        "what_not_to_do_simple": ["Do not guess the result", "Do not start, stop, or change medicine from an unclear report"],
        "machine_reading_limitations": extracted.get("unreadable_or_missing_parts", []),
        "assumptions_made": ["The report was not clear enough for safe reading."],
        "what_i_read_before_analysis": extracted.get("what_i_read", []),
        "unreadable_or_unclear_items": extracted.get("unreadable_or_missing_parts", []),
        "no_handwritten_analysis_note": "No detailed handwritten analysis is provided because a wrong reading can harm the patient.",
        "test_accuracy_and_false_result_considerations": extracted.get("test_accuracy_limitations", []),
        "low_medium_high_risk_areas": [],
        "what_each_risk_area_means": [],
        "possible_reasons_not_diagnosis": [],
        "what_to_ask_doctor": ["Please review the original report with a doctor or upload a clearer typed copy."],
        "possible_treatment_directions_to_discuss": [],
        "additional_tests_to_discuss": ["Ask the doctor if a repeat test or clearer scan/report is needed."],
        "supportive_non_medicine_care": ["Do not use home remedies or lifestyle changes as a substitute for a doctor review of an unclear report."],
        "safe_home_comfort_steps": ["Rest in a safe place and keep regular prescribed care unchanged unless a doctor says otherwise."],
        "meditation_psychological_support": ["If you feel worried, use slow breathing or quiet meditation for stress support while arranging doctor review."],
        "calm_food_medicine_sop": ["Take only prescribed medicines as directed. Sit calmly, read the label/instruction, and avoid rushing."],
        "suggested_meditation_videos": [],
        "what_to_avoid_until_doctor_review": ["Do not start, stop, or change medicine based on this unclear report."],
        "emergency_warning_signs": ["Contact a doctor or nearby medical service now if there is chest pain, severe breathlessness, fainting, severe bleeding, stroke-like symptoms, or severe allergic reaction."],
        "trend_from_previous_reports": "Trend was not compared because each AI check is independent for safety and accuracy.",
        "simple_patient_summary": "The uploaded report is not safe enough for AI interpretation. Please upload a clear typed report.",
        "doctor_note": "Uploaded report was not safely readable by AI; clinician review of original document is needed.",
        "limitations": ["No diagnosis given.", "No medicine recommendation given.", "Report was unclear or handwritten."],
        "confidence": "Low",
    }


@router.post("/v1/report/upload", response_model=UploadResponse)
async def upload_report(
    request: Request,
    files: list[UploadFile] = File(default=[]),
    device_id: str = Form(...),
    language: str = Form(default="auto"),
    patient_profile_json: str = Form(default="{}"),
    additional_report_text: str = Form(default=""),
    _: None = Depends(require_app_token),
) -> UploadResponse:
    request_id = str(uuid.uuid4())
    start = time.perf_counter()
    settings = get_settings()
    try:
        check_rate_limit(request, device_id)
        has_text = bool((additional_report_text or "").strip())
        if not files and not has_text:
            raise HTTPException(status_code=400, detail="At least one report file or pasted report text is required.")
        if len(files) > MAX_FILES_PER_REPORT:
            raise HTTPException(status_code=400, detail=f"Maximum {MAX_FILES_PER_REPORT} files allowed in one report.")

        # V19 independence rule: use only profile values explicitly supplied in this run.
        # A saved profile/report may remain visible in history, but it is never loaded as AI context.
        profile = _parse_profile(patient_profile_json)
        if profile:
            upsert_patient(device_id, profile)

        report_id = str(uuid.uuid4())
        stored_dir = settings.storage_path / "uploads" / device_id[:80] / report_id
        stored_dir.mkdir(parents=True, exist_ok=True)
        openai_files: list[dict[str, Any]] = []
        manifest: list[dict[str, str]] = []
        digest = hashlib.sha256()
        display_names: list[str] = []

        for idx, file in enumerate(files, start=1):
            content_type = _validate_upload(file)
            data = await _read_upload(file)
            digest.update(data)
            original_filename = _safe_filename(file.filename or f"medical_report_{idx}")
            display_names.append(original_filename)
            stored_path = stored_dir / f"{idx:02d}_{original_filename}.enc"
            stored_path.write_bytes(encrypt_bytes(data))
            manifest.append({
                "filename": original_filename,
                "content_type": content_type,
                "stored_path": str(stored_path),
                "sha256": hashlib.sha256(data).hexdigest(),
            })
            openai_files.append({"filename": original_filename, "content_type": content_type, "data": data})

        cleaned_text = (additional_report_text or "").strip()
        if cleaned_text:
            text_data = cleaned_text.encode("utf-8")
            digest.update(text_data)
            display_names.append("User pasted report text")
            text_path = stored_dir / "00_user_pasted_report_text.txt.enc"
            text_path.write_bytes(encrypt_bytes(text_data))
            manifest.append({
                "filename": "user_pasted_report_text.txt",
                "content_type": "text/plain",
                "stored_path": str(text_path),
                "sha256": hashlib.sha256(text_data).hexdigest(),
            })
            openai_files.append({"filename": "user_pasted_report_text.txt", "content_type": "text/plain", "data": text_data})

        manifest_path = stored_dir / "manifest.json.enc"
        manifest_path.write_bytes(encrypt_bytes(json.dumps(manifest, ensure_ascii=False).encode("utf-8")))
        original_filename = ", ".join(display_names)[:240]
        create_report(
            report_id=report_id,
            device_id=device_id,
            original_filename=original_filename,
            content_type="multi/mixed" if len(openai_files) > 1 else manifest[0]["content_type"],
            stored_path=str(stored_dir),
            file_sha256=digest.hexdigest(),
        )

        extracted = await extract_report_files(files=openai_files, language=language, patient_profile=profile)
        question_meta = extracted.pop("_question_generation_meta", {}) if isinstance(extracted, dict) else {}
        questions = extracted.get("questions") if isinstance(extracted.get("questions"), list) else []
        status_value = "needs_clear_typed_report" if extracted.get("document_quality", {}).get("handwritten_or_unclear") else "extracted"
        update_extraction(report_id, extracted, questions, status=status_value)
        audit_event(
            request_id=request_id,
            endpoint="/v1/report/upload",
            device_id=device_id,
            model_used=settings.openai_extraction_model,
            latency_ms=_latency_ms(start),
            success=True,
            event_json={
                "report_id": report_id,
                "file_count": len(files),
                "typed_text_used": has_text,
                "status": status_value,
                "question_count": len(questions),
                "question_options_validated": bool(question_meta.get("options_validated", False)),
                "question_fallback_count": int(question_meta.get("fallback_count", 0) or 0),
                "question_generation_source": str(question_meta.get("source", "unknown"))[:80],
                "historical_context_used": False,
                "current_run_only": True,
            },
        )
        audit_event(
            request_id=request_id,
            endpoint="/v1/audit/clarifying_questions_generated",
            device_id=device_id,
            model_used=settings.openai_extraction_model,
            latency_ms=0,
            success=True,
            event_json={
                "report_id": report_id,
                "question_count": len(questions),
                "options_validated": bool(question_meta.get("options_validated", False)),
                "fallback_used": int(question_meta.get("fallback_count", 0) or 0) > 0,
                "fallback_count": int(question_meta.get("fallback_count", 0) or 0),
                "historical_context_used": False,
            },
        )
        return UploadResponse(
            request_id=request_id,
            report_id=report_id,
            status=status_value,
            safety_note="AI can misread reports. Please check the extracted values below before final analysis. This is not a diagnosis or prescription.",
            extracted=extracted,
            questions=questions,
            next_step="Confirm what was read, answer all questions, choose language, then call /v1/report/analyze.",
        )
    except HTTPException as exc:
        audit_event(request_id=request_id, endpoint="/v1/report/upload", device_id=device_id, model_used=settings.openai_extraction_model, latency_ms=_latency_ms(start), success=False, error_type=_http_error_type(exc))
        raise
    except Exception as exc:
        audit_event(request_id=request_id, endpoint="/v1/report/upload", device_id=device_id, model_used=settings.openai_extraction_model, latency_ms=_latency_ms(start), success=False, error_type=exc.__class__.__name__)
        raise HTTPException(status_code=502, detail="Could not read these reports safely. Please try clearer typed PDF/image files.") from exc


@router.post("/v1/report/analyze", response_model=AnalyzeResponse)
async def analyze_existing_report(request: Request, req: AnalyzeRequest, _: None = Depends(require_app_token)) -> AnalyzeResponse:
    request_id = str(uuid.uuid4())
    start = time.perf_counter()
    settings = get_settings()
    try:
        check_rate_limit(request, req.device_id)
        report = get_report(req.report_id, req.device_id)
        if not report:
            raise HTTPException(status_code=404, detail="Report not found")
        extracted = report.get("extracted") or {}
        quality = extracted.get("document_quality") if isinstance(extracted.get("document_quality"), dict) else {}
        if quality.get("handwritten_or_unclear") is True or quality.get("readable") is False:
            analysis = _unclear_analysis(req, extracted)
            update_analysis(req.report_id, analysis, status="needs_clear_typed_report")
            audit_event(
                request_id=request_id,
                endpoint="/v1/report/analyze",
                device_id=req.device_id,
                model_used=None,
                latency_ms=_latency_ms(start),
                success=True,
                event_json={"report_id": req.report_id, "status": "needs_clear_typed_report", "model_skipped": True},
            )
            return AnalyzeResponse(request_id=request_id, report_id=req.report_id, status="needs_clear_typed_report", analysis=analysis)

        profile = req.patient_profile.model_dump()
        if profile:
            upsert_patient(req.device_id, profile)
        # V18: each analysis run is independent.
        # Do not use old saved reports as context because the same phone may contain reports from different people/issues.
        # Saved reports remain available only for user viewing/download from history.
        compact_history = []
        analysis = await analyze_report(
            extracted=extracted,
            language=req.language,
            patient_profile=profile,
            user_answers=req.user_answers,
            extraction_corrections=req.extraction_corrections,
            history=compact_history,
            youtube_video_links=req.youtube_video_links,
        )
        update_analysis(req.report_id, analysis, status="analyzed")
        audit_event(
            request_id=request_id,
            endpoint="/v1/report/analyze",
            device_id=req.device_id,
            model_used=settings.openai_medical_model,
            latency_ms=_latency_ms(start),
            success=True,
            event_json={
                "report_id": req.report_id,
                "independent_run_no_history": True,
                "historical_context_used": False,
                "same_patient_previous_report_used": False,
                "current_run_answer_count": len(req.user_answers),
                "saved_history_remains_viewable": True,
            },
        )
        return AnalyzeResponse(request_id=request_id, report_id=req.report_id, status="analyzed", analysis=analysis)
    except HTTPException as exc:
        audit_event(request_id=request_id, endpoint="/v1/report/analyze", device_id=req.device_id, model_used=settings.openai_medical_model, latency_ms=_latency_ms(start), success=False, error_type=_http_error_type(exc), event_json={"report_id": req.report_id})
        raise
    except Exception as exc:
        audit_event(request_id=request_id, endpoint="/v1/report/analyze", device_id=req.device_id, model_used=settings.openai_medical_model, latency_ms=_latency_ms(start), success=False, error_type=exc.__class__.__name__, event_json={"report_id": req.report_id})
        raise HTTPException(status_code=502, detail="Could not analyze this report safely. Please try again or consult a doctor directly.") from exc


@router.get("/v1/report/history", response_model=HistoryResponse)
async def history(request: Request, device_id: str, _: None = Depends(require_app_token)) -> HistoryResponse:
    request_id = str(uuid.uuid4())
    start = time.perf_counter()
    try:
        check_rate_limit(request, device_id)
        items = list_reports(device_id, limit=50, include_payloads=False)
        audit_event(request_id=request_id, endpoint="/v1/report/history", device_id=device_id, model_used=None, latency_ms=_latency_ms(start), success=True, event_json={"item_count": len(items)})
        return HistoryResponse(items=items)
    except HTTPException as exc:
        audit_event(request_id=request_id, endpoint="/v1/report/history", device_id=device_id, model_used=None, latency_ms=_latency_ms(start), success=False, error_type=_http_error_type(exc))
        raise


@router.get("/v1/report/{report_id}")
async def read_report(report_id: str, device_id: str, _: None = Depends(require_app_token)) -> dict[str, Any]:
    request_id = str(uuid.uuid4())
    start = time.perf_counter()
    report = get_report(report_id, device_id)
    if not report:
        audit_event(request_id=request_id, endpoint="/v1/report/read", device_id=device_id, model_used=None, latency_ms=_latency_ms(start), success=False, error_type="not_found", event_json={"report_id": report_id})
        raise HTTPException(status_code=404, detail="Report not found")
    report.pop("stored_path", None)
    audit_event(request_id=request_id, endpoint="/v1/report/read", device_id=device_id, model_used=None, latency_ms=_latency_ms(start), success=True, event_json={"report_id": report_id})
    return report


@router.delete("/v1/report/{report_id}", response_model=DeleteResponse)
async def remove_report(report_id: str, device_id: str, _: None = Depends(require_app_token)) -> DeleteResponse:
    request_id = str(uuid.uuid4())
    start = time.perf_counter()
    ok = delete_report(report_id, device_id)
    audit_event(request_id=request_id, endpoint="/v1/report/delete", device_id=device_id, model_used=None, latency_ms=_latency_ms(start), success=ok, error_type=None if ok else "not_found", event_json={"report_id": report_id})
    return DeleteResponse(ok=ok)


def _client_payload_event(payload: dict[str, Any], default_event: str) -> tuple[str, str | None, dict[str, Any]]:
    event = str(payload.get("event", default_event))[:80]
    device_id = str(payload.get("device_id", ""))[:160] or None
    safe_payload = {
        "event": event,
        "screen": str(payload.get("screen", ""))[:80],
        "app_version": str(payload.get("app_version", ""))[:40],
        "source": str(payload.get("source", "android_app"))[:80],
    }
    for key in ("plan", "remaining_credits", "report_id", "debug"):
        if key in payload:
            safe_payload[key] = payload.get(key)
    return event, device_id, safe_payload


@router.post("/v1/audit/client")
async def client_audit_event(payload: dict[str, Any] = Body(default_factory=dict), _: None = Depends(require_app_token)) -> dict[str, Any]:
    request_id = str(uuid.uuid4())
    event, device_id, safe_payload = _client_payload_event(payload, "client_event")
    audit_event(request_id=request_id, endpoint=f"/v1/audit/client:{event}", device_id=device_id, model_used=None, latency_ms=0, success=True, event_json=safe_payload)
    return {"ok": True, "request_id": request_id}


@router.post("/v1/audit/credit")
async def credit_audit_event(payload: dict[str, Any] = Body(default_factory=dict), _: None = Depends(require_app_token)) -> dict[str, Any]:
    request_id = str(uuid.uuid4())
    event, device_id, safe_payload = _client_payload_event(payload, "credit_event")
    # This is only an audit log. It does NOT verify payment. Real production billing still needs Google Play purchase verification.
    audit_event(request_id=request_id, endpoint=f"/v1/audit/credit:{event}", device_id=device_id, model_used=None, latency_ms=0, success=True, event_json=safe_payload)
    return {"ok": True, "request_id": request_id}
