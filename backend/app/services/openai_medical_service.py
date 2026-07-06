import base64
import json
import re
from typing import Any

from app.core.config import get_settings


EXTRACTION_SCHEMA: dict[str, Any] = {
    "type": "object",
    "additionalProperties": False,
    "properties": {
        "detected_language": {"type": "string"},
        "document_type": {"type": "string"},
        "files_read": {"type": "array", "items": {"type": "string"}},
        "document_quality": {
            "type": "object",
            "additionalProperties": False,
            "properties": {
                "readable": {"type": "boolean"},
                "typed_or_printed": {"type": "boolean"},
                "handwritten_or_unclear": {"type": "boolean"},
                "quality_notes": {"type": "string"},
            },
            "required": ["readable", "typed_or_printed", "handwritten_or_unclear", "quality_notes"],
        },
        "patient_identifiers_found": {
            "type": "object",
            "additionalProperties": False,
            "properties": {
                "name": {"type": "string"},
                "age": {"type": "string"},
                "sex": {"type": "string"},
                "report_date": {"type": "string"},
                "lab_or_hospital": {"type": "string"},
                "doctor": {"type": "string"},
            },
            "required": ["name", "age", "sex", "report_date", "lab_or_hospital", "doctor"],
        },
        "what_i_read": {"type": "array", "items": {"type": "string"}},
        "test_results": {
            "type": "array",
            "items": {
                "type": "object",
                "additionalProperties": False,
                "properties": {
                    "source_file": {"type": "string"},
                    "section": {"type": "string"},
                    "test_name": {"type": "string"},
                    "value": {"type": "string"},
                    "unit": {"type": "string"},
                    "reference_range": {"type": "string"},
                    "flag": {"type": "string", "enum": ["Low", "Normal", "High", "Critical", "Unclear", "Not provided"]},
                    "confidence": {"type": "integer", "minimum": 0, "maximum": 100},
                    "raw_text": {"type": "string"},
                },
                "required": ["source_file", "section", "test_name", "value", "unit", "reference_range", "flag", "confidence", "raw_text"],
            },
        },
        "test_accuracy_limitations": {"type": "array", "items": {"type": "string"}},
        "unreadable_or_missing_parts": {"type": "array", "items": {"type": "string"}},
        "needs_user_confirmation": {"type": "boolean"},
        "questions": {
            "type": "array",
            "minItems": 0,
            "maxItems": 8,
            "items": {
                "type": "object",
                "additionalProperties": False,
                "properties": {
                    "id": {"type": "string"},
                    "question": {"type": "string"},
                    "why_needed": {"type": "string"},
                    "answer_type": {
                        "type": "string",
                        "enum": ["single_select", "multi_select", "yes_no", "number", "date", "free_text"],
                    },
                    "answer_options": {"type": "array", "items": {"type": "string"}, "minItems": 0, "maxItems": 12},
                    "allow_other": {"type": "boolean"},
                    "required": {"type": "boolean"},
                    "placeholder": {"type": "string"},
                },
                "required": [
                    "id",
                    "question",
                    "why_needed",
                    "answer_type",
                    "answer_options",
                    "allow_other",
                    "required",
                    "placeholder",
                ],
            },
        },
        "safe_message": {"type": "string"},
    },
    "required": ["detected_language", "document_type", "files_read", "document_quality", "patient_identifiers_found", "what_i_read", "test_results", "test_accuracy_limitations", "unreadable_or_missing_parts", "needs_user_confirmation", "questions", "safe_message"],
}


ANALYSIS_SCHEMA: dict[str, Any] = {
    "type": "object",
    "additionalProperties": False,
    "properties": {
        "title": {"type": "string"},
        "detected_language": {"type": "string"},
        "safety_note": {"type": "string"},
        "final_conclusion_simple": {"type": "string"},
        "what_to_tell_doctor_simple": {"type": "array", "items": {"type": "string"}},
        "ai_safe_next_options_to_discuss": {"type": "array", "items": {"type": "string"}},
        "seriousness_in_simple_words": {"type": "string"},
        "red_amber_green_boxes": {
            "type": "array",
            "minItems": 1,
            "maxItems": 8,
            "items": {
                "type": "object",
                "additionalProperties": False,
                "properties": {
                    "color": {"type": "string", "enum": ["Green", "Amber", "Red"]},
                    "title": {"type": "string"},
                    "simple_message": {"type": "string"},
                    "what_to_do": {"type": "string"},
                    "why_this_color": {"type": "string"},
                },
                "required": ["color", "title", "simple_message", "what_to_do", "why_this_color"],
            },
        },
        "emergency_escalation_needed": {"type": "boolean"},
        "emergency_escalation_reason": {"type": "string"},
        "action_checklist": {"type": "array", "items": {"type": "string"}},
        "plain_language_glossary": {
            "type": "array",
            "items": {
                "type": "object",
                "additionalProperties": False,
                "properties": {
                    "term": {"type": "string"},
                    "simple_meaning": {"type": "string"},
                },
                "required": ["term", "simple_meaning"],
            },
        },
        "when_it_can_become_serious": {"type": "array", "items": {"type": "string"}},
        "simple_next_steps_now": {"type": "array", "items": {"type": "string"}},
        "home_support_simple": {"type": "array", "items": {"type": "string"}},
        "what_not_to_do_simple": {"type": "array", "items": {"type": "string"}},
        "machine_reading_limitations": {"type": "array", "items": {"type": "string"}},
        "assumptions_made": {"type": "array", "items": {"type": "string"}},
        "what_i_read_before_analysis": {"type": "array", "items": {"type": "string"}},
        "unreadable_or_unclear_items": {"type": "array", "items": {"type": "string"}},
        "no_handwritten_analysis_note": {"type": "string"},
        "test_accuracy_and_false_result_considerations": {"type": "array", "items": {"type": "string"}},
        "low_medium_high_risk_areas": {
            "type": "array",
            "items": {
                "type": "object",
                "additionalProperties": False,
                "properties": {
                    "risk_level": {"type": "string", "enum": ["Low", "Medium", "High"]},
                    "area": {"type": "string"},
                    "evidence_from_report": {"type": "string"},
                    "what_it_may_mean_simple": {"type": "string"},
                    "test_accuracy_consideration": {"type": "string"},
                    "why_not_definitive": {"type": "string"},
                    "doctor_priority": {"type": "string", "enum": ["Routine", "Soon", "Urgent"]},
                },
                "required": ["risk_level", "area", "evidence_from_report", "what_it_may_mean_simple", "test_accuracy_consideration", "why_not_definitive", "doctor_priority"],
            },
        },
        "what_each_risk_area_means": {"type": "array", "items": {"type": "string"}},
        "possible_reasons_not_diagnosis": {"type": "array", "items": {"type": "string"}},
        "what_to_ask_doctor": {"type": "array", "items": {"type": "string"}},
        "possible_treatment_directions_to_discuss": {"type": "array", "items": {"type": "string"}},
        "additional_tests_to_discuss": {"type": "array", "items": {"type": "string"}},
        "supportive_non_medicine_care": {"type": "array", "items": {"type": "string"}},
        "safe_home_comfort_steps": {"type": "array", "items": {"type": "string"}},
        "meditation_psychological_support": {"type": "array", "items": {"type": "string"}},
        "calm_food_medicine_sop": {"type": "array", "items": {"type": "string"}},
        "suggested_meditation_videos": {"type": "array", "items": {"type": "string"}},
        "what_to_avoid_until_doctor_review": {"type": "array", "items": {"type": "string"}},
        "emergency_warning_signs": {"type": "array", "items": {"type": "string"}},
        "trend_from_previous_reports": {"type": "string"},
        "simple_patient_summary": {"type": "string"},
        "doctor_note": {"type": "string"},
        "limitations": {"type": "array", "items": {"type": "string"}},
        "confidence": {"type": "string", "enum": ["Low", "Medium", "High"]},
    },
    "required": ["title", "detected_language", "safety_note", "final_conclusion_simple", "what_to_tell_doctor_simple", "ai_safe_next_options_to_discuss", "seriousness_in_simple_words", "red_amber_green_boxes", "emergency_escalation_needed", "emergency_escalation_reason", "action_checklist", "plain_language_glossary", "when_it_can_become_serious", "simple_next_steps_now", "home_support_simple", "what_not_to_do_simple", "machine_reading_limitations", "assumptions_made", "what_i_read_before_analysis", "unreadable_or_unclear_items", "no_handwritten_analysis_note", "test_accuracy_and_false_result_considerations", "low_medium_high_risk_areas", "what_each_risk_area_means", "possible_reasons_not_diagnosis", "what_to_ask_doctor", "possible_treatment_directions_to_discuss", "additional_tests_to_discuss", "supportive_non_medicine_care", "safe_home_comfort_steps", "meditation_psychological_support", "calm_food_medicine_sop", "suggested_meditation_videos", "what_to_avoid_until_doctor_review", "emergency_warning_signs", "trend_from_previous_reports", "simple_patient_summary", "doctor_note", "limitations", "confidence"],
}


EXTRACTION_INSTRUCTIONS = """
You are a careful medical report reading assistant.
Your job is to read multiple typed/printed medical reports, PDFs, screenshots, images, lab reports, scans, discharge summaries, and doctor-readable documents.
You do NOT diagnose. You do NOT prescribe medicine. You do NOT provide medicine dose.
First extract what is visible from each file. If multiple files are uploaded in this current run, combine only those current-run files into one context and mention file names in files_read and source_file.
Never use saved reports, old chats, prior runs, previous report IDs, or historical transactions as medical context. Every run is independent.
If the report is handwritten, too blurry, incomplete, cropped, or unsafe to read, clearly mark handwritten_or_unclear=true and avoid interpretation.
Add test_accuracy_limitations when a test can have false negative/false positive or timing/sample/method limitations. Example: infectious disease antigen tests can be negative early in illness and may need repeat/confirmatory testing by a doctor.
Always use probabilistic language. Never say the patient has a disease.

Generate only questions that are genuinely needed to understand the CURRENT report/symptoms. Zero questions is allowed when nothing important is missing. Do not repeat facts already present in the current patient profile or current uploaded report.
Generate every question together with its own answer control:
- yes_no: only for a real yes/no question, such as "Do you have fever?". Use answer_options Yes, No, Not sure in the user's language.
- single_select: one choice from question-specific options, such as exact body location, duration band, severity band, frequency, side, timing, or symptom pattern.
- multi_select: more than one choice may apply, such as a list of current symptoms.
- number: a numeric value is needed. Keep answer_options empty and provide a simple placeholder including the unit if useful.
- date: a date is needed. Keep answer_options empty and provide a simple date placeholder.
- free_text: use only when safe, useful choices cannot be listed. Keep answer_options empty and provide a simple placeholder.

Answer options must directly answer the exact question. Never use generic Yes / No / Don't know for location, side, duration, severity, amount, frequency, timing, body part, symptom type, or "which" questions.
Examples:
- "Where is the pain?" -> single_select with relevant body-location choices.
- "Which side is affected?" -> single_select: Left, Right, Both, Middle/central, Not sure.
- "How long has it been present?" -> single_select with duration ranges.
- "How strong is it?" -> single_select with No symptom, Mild, Moderate, Strong, Very strong, Not sure.
- "Which symptoms are present?" -> multi_select with report-specific symptom choices.
Use the same language as the user's selected language for the question, options, reason, and placeholder.
Set allow_other=true only when an unlisted answer is reasonably possible. Include Other/अन्य when allow_other=true. Include Not sure/पता नहीं where medically sensible.
Avoid questions requiring medical knowledge. Keep each question and option short and simple.
Return only JSON matching the schema.
""".strip()


ANALYSIS_INSTRUCTIONS = """
You are a medical report explainer for patients and doctors.
Your goal is to help the user understand typed medical reports and prepare for a doctor visit.
Critical safety rules:
- Do not diagnose. Say may suggest, may be associated with, can be seen with, should be reviewed by a doctor. Always clearly say this is AI-generated and can make mistakes.
- Do not recommend any medicine name, dose, prescription, stopping medicine, or starting medicine.
- Non-medicine/home care must be only safe supportive comfort steps, not cures. Avoid herbs, supplements, detox, fasting, or disease-specific home remedies unless clearly doctor-approved. If hydration/rest/food advice could be unsafe for kidney, heart, pregnancy, diabetes, surgery or other conditions, tell user to confirm with doctor.
- The user may upload files or may only type symptoms/report values. File upload is not mandatory. If only typed details are given, say confidence may be lower unless a doctor checks the original report.
- Give possible treatment directions only as broad categories to discuss with a doctor, such as medicine discussion with doctor, surgery/procedure discussion, watch-and-wait, repeat test, specialist review, imaging, hydration review, infection evaluation, or doctor-supervised medication discussion. Do not tell the user which option to choose.
- additional_tests_to_discuss must list simple tests/scans/checks the user can ask the doctor about only if relevant. Keep it short and simple. Examples: repeat CBC, iron studies, thyroid test, urine test, ultrasound follow-up, MRI, sugar test, kidney/liver test, specialist review. Say "ask doctor if needed", never "you must do this".
- Include test_accuracy_and_false_result_considerations only when useful. Keep it very short: maximum 1 bullet if possible, simple words. This is about reliability of the actual medical test/report itself, not about AI technology. Example: some infection tests can miss disease early, or sample timing/quality can affect results, so a doctor may repeat/confirm. Clearly say how much the report/test can be trusted through confidence = Low/Medium/High.
- For urgent medical warning signs, do not create panic and do not print emergency phone numbers in the user-facing answer. Use calm wording such as: "contact a doctor or nearby medical service now if symptoms are strong or getting worse." Avoid fear-heavy Hindi wording.
- If handwritten_or_unclear is true, refuse detailed analysis and ask for typed/clear report.
- Respond in the user's chosen language for every user-facing field, including care priority, safety note, doctor-facing note heading content, confidence/trust explanations, and medical-help guidance. Keep language extremely simple, like explaining to a non-technical, non-medical village user. Use short sentences. Avoid jargon; if a medical word is necessary, explain it in plain words.
- Put the most useful answer first: final_conclusion_simple, what_to_tell_doctor_simple, ai_safe_next_options_to_discuss, additional_tests_to_discuss, seriousness_in_simple_words, red_amber_green_boxes, and when_it_can_become_serious.
- final_conclusion_simple must be extremely simple: 2 to 4 short sentences, no jargon, no fear language, no technical words. Do not use phrases like "your easy report" or "show this report to doctor".
- what_to_tell_doctor_simple must be 3 to 5 very simple lines a village user can read aloud to a doctor. Use words like “Doctor, please check this report.” Do not use technical words unless unavoidable.
- ai_safe_next_options_to_discuss and possible_treatment_directions_to_discuss must not sound like direct treatment advice. They should be safe options to ask the doctor about, such as: whether medicine is needed, whether surgery/procedure is needed, whether watch-and-wait is enough, whether repeat test/scans are needed, whether a specialist is needed, what warning symptoms to watch, and what food/lifestyle changes are safe. Make these very simple so the user is not fooled or confused, but do not tell the user which treatment to take.
- Avoid complex words in top fields. If a medical word is unavoidable, define it in the glossary.
- Red / Amber / Green meaning: Green = appears okay/low concern but monitor; Amber = needs doctor review soon; Red = needs faster doctor/medical-service review because symptoms or report items could be important. Do not over-alarm. Do not hide risk.

- Add emergency_escalation_needed=true if red-flag symptoms or report signals suggest faster medical review. Keep emergency_escalation_reason very simple and calm.
- action_checklist must be 3 to 6 checkbox-friendly actions, e.g. book doctor appointment, share PDF with doctor, carry original report, note symptoms, repeat/confirm test if doctor says.
- plain_language_glossary must define difficult report terms in very simple words, e.g. hs-CRP, ESR, myometrium, adenomyosis, cyst, lesion, creatinine. Include only terms appearing in the report or answer.
- Tone must be warm and human: say “Nothing immediately alarming was found” instead of cold or technical phrases.
- Do not use the phrase “our recommendation” in a way that sounds like medical advice. Say “safe options to discuss with doctor”. doctor_note is a note for the doctor, not a doctor's note. Keep it concise and useful for a clinician.
- Do not repeat report values already printed on the report in the top summary. Mention only what matters. Put detailed readings only in bottom fields.
- machine_reading_limitations must list anything not machine-readable, handwritten, cropped, blurred, missing, or uncertain.
- assumptions_made must list assumptions used because the user did not know or did not provide an answer.
- Always include what AI read from report before interpretation, but keep it at the bottom for safety.
- Treat every run as independent. Do not use saved report history or previous reports for interpretation. If asked about trend_from_previous_reports, say trend was not compared because each check is independent for safety and accuracy.
- Meditation/psychological support may include Rajyoga-style soul consciousness and positive thoughts as stress support only, not as treatment for disease. You may include: see yourself as a soul at the center of the forehead, remember Supreme Soul Shiv, thank the body for supporting you, remember good actions, keep the mind peaceful because mental calm supports the treatment journey.
- Calm food/medicine SOP may include: sit calmly, read label/doctor instruction, take only prescribed medicines, avoid rushing, say gratitude/prayer if personally meaningful, keep water/food in a clean place, and use positive intention as a spiritual wellness practice. Do not mention Dr Emoto. Do not claim thoughts change lab values or cure illness.
- If YouTube links/titles/tags are supplied, suggest the best relevant meditation video using only the provided link/title/tag text. Do not claim you watched the video.
Return only JSON matching the schema.
""".strip()



QUESTION_TYPES = {"single_select", "multi_select", "yes_no", "number", "date", "free_text"}


def _is_hindi(value: str) -> bool:
    text = value or ""
    return any("\u0900" <= ch <= "\u097f" for ch in text) or "hindi" in text.lower() or "hinglish" in text.lower()


def _clean_text(value: Any, max_length: int) -> str:
    return " ".join(str(value or "").strip().split())[:max_length]


def _dedupe_options(values: Any) -> list[str]:
    if not isinstance(values, list):
        return []
    result: list[str] = []
    seen: set[str] = set()
    for value in values:
        option = _clean_text(value, 100)
        key = option.casefold()
        if option and key not in seen:
            seen.add(key)
            result.append(option)
    return result[:12]


def _question_is_yes_no(question: str) -> bool:
    q = question.strip().lower()
    starters = (
        "do ", "does ", "did ", "is ", "are ", "was ", "were ", "have ", "has ",
        "can ", "could ", "क्या ", "क्‍या ", "है क्या", "हैं क्या",
    )
    return q.startswith(starters) or q.endswith("?") and any(token in q for token in ("do you", "have you", "is there", "are you", "क्या"))


def _generic_yes_no_only(options: list[str]) -> bool:
    generic = {
        "yes", "no", "not sure", "don't know", "do not know", "unknown", "not applicable",
        "हाँ", "हां", "नहीं", "पता नहीं", "मालूम नहीं", "लागू नहीं",
    }
    meaningful = [option.casefold() for option in options if option.casefold() not in generic]
    return len(meaningful) == 0


def _localized_common(language: str, question: str) -> dict[str, str]:
    hindi = _is_hindi(language) or _is_hindi(question)
    return {
        "yes": "हाँ" if hindi else "Yes",
        "no": "नहीं" if hindi else "No",
        "not_sure": "पता नहीं" if hindi else "Not sure",
        "other": "अन्य" if hindi else "Other",
        "free_text": "अपना उत्तर सरल शब्दों में लिखें" if hindi else "Type your answer in simple words",
        "number": "संख्या और unit लिखें, यदि पता हो" if hindi else "Type the number and unit, if known",
        "date": "तारीख लिखें, यदि पता हो" if hindi else "Type the date, if known",
    }


def normalize_clarifying_questions(raw_questions: Any, language: str) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    """Validate model-created question controls without using any historical report context.

    The same extraction LLM call creates each question and its answer options. This function
    keeps valid question-specific options, removes duplicates, and falls back to free text
    instead of showing unsafe generic choices for a non-yes/no question.
    """
    questions = raw_questions if isinstance(raw_questions, list) else []
    normalized: list[dict[str, Any]] = []
    used_ids: set[str] = set()
    fallback_count = 0
    fallback_reasons: list[str] = []

    for index, raw in enumerate(questions[:8], start=1):
        if not isinstance(raw, dict):
            fallback_count += 1
            fallback_reasons.append(f"q{index}:invalid_object")
            continue

        question = _clean_text(raw.get("question"), 500)
        if not question:
            fallback_count += 1
            fallback_reasons.append(f"q{index}:empty_question")
            continue

        raw_id = re.sub(r"[^A-Za-z0-9_-]+", "_", _clean_text(raw.get("id"), 80)).strip("_")
        question_id = raw_id or f"q{index}"
        base_id = question_id
        suffix = 2
        while question_id in used_ids:
            question_id = f"{base_id}_{suffix}"
            suffix += 1
        used_ids.add(question_id)

        why_needed = _clean_text(raw.get("why_needed"), 500) or (
            "यह उत्तर doctor को report समझने में मदद कर सकता है।"
            if _is_hindi(language) or _is_hindi(question)
            else "This answer can help a doctor understand the current report."
        )
        answer_type = _clean_text(raw.get("answer_type"), 40).lower()
        if answer_type not in QUESTION_TYPES:
            answer_type = "single_select" if raw.get("answer_options") else "free_text"

        options = _dedupe_options(raw.get("answer_options"))
        allow_other = bool(raw.get("allow_other", False))
        required = bool(raw.get("required", True))
        common = _localized_common(language, question)
        placeholder = _clean_text(raw.get("placeholder"), 180)
        used_fallback = False
        fallback_reason = ""

        if answer_type == "yes_no":
            if not _question_is_yes_no(question):
                answer_type = "free_text"
                options = []
                allow_other = False
                placeholder = placeholder or common["free_text"]
                used_fallback = True
                fallback_reason = "yes_no_type_for_non_yes_no_question"
            else:
                options = [common["yes"], common["no"], common["not_sure"]]
                allow_other = False
                placeholder = ""
        elif answer_type in {"number", "date", "free_text"}:
            options = []
            allow_other = False
            if not placeholder:
                placeholder = common[answer_type]
        else:
            # For select controls, the model must provide choices that answer this exact question.
            if len(options) < 2 or _generic_yes_no_only(options):
                answer_type = "free_text"
                options = []
                allow_other = False
                placeholder = placeholder or common["free_text"]
                used_fallback = True
                fallback_reason = "missing_or_generic_options"
            else:
                if allow_other and common["other"].casefold() not in {item.casefold() for item in options}:
                    options.append(common["other"])
                if common["not_sure"].casefold() not in {item.casefold() for item in options}:
                    options.append(common["not_sure"])
                options = options[:12]
                placeholder = ""

        if used_fallback:
            fallback_count += 1
            fallback_reasons.append(f"{question_id}:{fallback_reason}")

        normalized.append(
            {
                "id": question_id,
                "question": question,
                "why_needed": why_needed,
                "answer_type": answer_type,
                "answer_options": options,
                "allow_other": allow_other,
                "required": required,
                "placeholder": placeholder,
            }
        )

    return normalized, {
        "source": "same_extraction_llm_call",
        "question_count": len(normalized),
        "options_validated": True,
        "fallback_count": fallback_count,
        "fallback_reasons": fallback_reasons,
        "historical_context_used": False,
        "current_run_only": True,
    }

def _client():
    from openai import AsyncOpenAI
    settings = get_settings()
    if not settings.openai_api_key:
        raise RuntimeError("OPENAI_API_KEY is not set")
    return AsyncOpenAI(api_key=settings.openai_api_key)


def _extract_output_text(response: Any) -> str:
    text = getattr(response, "output_text", None)
    if text:
        return text.strip()
    chunks: list[str] = []
    try:
        for item in response.output:
            if getattr(item, "type", "") == "message":
                for content in item.content:
                    value = getattr(content, "text", None)
                    if value:
                        chunks.append(value)
    except Exception:
        pass
    return "\n".join(chunks).strip()


def _safe_json_loads(text: str) -> dict[str, Any]:
    try:
        return json.loads(text)
    except json.JSONDecodeError as exc:
        start = text.find("{")
        end = text.rfind("}")
        if start >= 0 and end > start:
            return json.loads(text[start : end + 1])
        raise ValueError(f"Model returned invalid JSON: {text[:300]}") from exc


def _file_content_block(filename: str, content_type: str, data: bytes) -> dict[str, Any]:
    b64 = base64.b64encode(data).decode("utf-8")
    safe_name = filename or "medical_report"
    if content_type == "application/pdf" or safe_name.lower().endswith(".pdf"):
        return {
            "type": "input_file",
            "filename": safe_name,
            "file_data": f"data:application/pdf;base64,{b64}",
        }
    if content_type.startswith("text/") or safe_name.lower().endswith(".txt"):
        text_value = data.decode("utf-8", errors="replace")
        return {
            "type": "input_text",
            "text": f"User pasted report text or doctor note from {safe_name}:\n{text_value}",
        }
    image_type = content_type if content_type.startswith("image/") else "image/jpeg"
    return {
        "type": "input_image",
        "image_url": f"data:{image_type};base64,{b64}",
    }


async def _call_json_response(*, model: str, instructions: str, content: list[dict[str, Any]], schema: dict[str, Any], schema_name: str) -> dict[str, Any]:
    settings = get_settings()
    kwargs: dict[str, Any] = {
        "model": model,
        "instructions": instructions,
        "input": [{"role": "user", "content": content}],
        "text": {
            "format": {
                "type": "json_schema",
                "name": schema_name,
                "strict": True,
                "schema": schema,
            }
        },
    }
    if settings.openai_reasoning_effort != "none":
        kwargs["reasoning"] = {"effort": settings.openai_reasoning_effort}
    response = await _client().responses.create(**kwargs)
    return _safe_json_loads(_extract_output_text(response))


async def extract_report(*, filename: str, content_type: str, data: bytes, language: str, patient_profile: dict[str, Any]) -> dict[str, Any]:
    return await extract_report_files(
        files=[{"filename": filename, "content_type": content_type, "data": data}],
        language=language,
        patient_profile=patient_profile,
    )


async def extract_report_files(*, files: list[dict[str, Any]], language: str, patient_profile: dict[str, Any]) -> dict[str, Any]:
    content: list[dict[str, Any]] = []
    file_summaries: list[dict[str, str]] = []
    for item in files:
        filename = str(item.get("filename") or "medical_report")
        content_type = str(item.get("content_type") or "application/octet-stream")
        data = item.get("data") or b""
        content.append(_file_content_block(filename, content_type, data))
        file_summaries.append({"filename": filename, "content_type": content_type})
    content.append(
        {
            "type": "input_text",
            "text": json.dumps(
                {
                    "task": "Extract medical report content safely from all uploaded files. Ask missing context questions. Do not diagnose.",
                    "preferred_language": language or "auto",
                    "patient_profile_known_so_far": patient_profile,
                    "uploaded_files": file_summaries,
                },
                ensure_ascii=False,
            ),
        }
    )
    settings = get_settings()
    result = await _call_json_response(
        model=settings.openai_extraction_model,
        instructions=EXTRACTION_INSTRUCTIONS,
        content=content,
        schema=EXTRACTION_SCHEMA,
        schema_name="medical_report_extraction_v4",
    )
    normalized_questions, question_meta = normalize_clarifying_questions(
        result.get("questions"),
        str(result.get("detected_language") or language or "auto"),
    )
    result["questions"] = normalized_questions
    result["_question_generation_meta"] = question_meta
    return result


async def analyze_report(*, extracted: dict[str, Any], language: str, patient_profile: dict[str, Any], user_answers: dict[str, str], extraction_corrections: str, history: list[dict[str, Any]], youtube_video_links: list[str] | None = None) -> dict[str, Any]:
    content = [
        {
            "type": "input_text",
            "text": json.dumps(
                {
                    "task": "Explain medical report in simple language for doctor discussion.",
                    "preferred_language": language or "auto",
                    "extracted_report": extracted,
                    "patient_profile": patient_profile,
                    "user_answers": user_answers,
                    "user_corrections_to_extraction": extraction_corrections,
                    "previous_reports_for_trend_only": [],
                    "user_supplied_meditation_video_links_titles_or_tags": youtube_video_links or [],
                },
                ensure_ascii=False,
            ),
        }
    ]
    settings = get_settings()
    return await _call_json_response(model=settings.openai_medical_model, instructions=ANALYSIS_INSTRUCTIONS, content=content, schema=ANALYSIS_SCHEMA, schema_name="medical_report_analysis_v3")
