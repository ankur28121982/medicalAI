from app.schemas import AnalyzeRequest, PatientProfile
from app.services.openai_medical_service import normalize_clarifying_questions


def test_analyze_request_validates():
    req = AnalyzeRequest(report_id="12345678-abcd", device_id="device-1", patient_profile=PatientProfile(age="42"))
    assert req.device_id == "device-1"
    assert req.patient_profile.age == "42"
    assert req.continue_from_previous is False
    assert req.previous_report_id == ""


def test_question_specific_options_are_kept():
    questions, meta = normalize_clarifying_questions(
        [
            {
                "id": "pain_place",
                "question": "Where exactly is the pain?",
                "why_needed": "The exact place can change doctor review.",
                "answer_type": "single_select",
                "answer_options": ["Upper abdomen", "Lower abdomen", "Right side", "Left side", "Not sure"],
                "allow_other": True,
                "required": True,
                "placeholder": "",
            }
        ],
        "English",
    )
    assert questions[0]["answer_type"] == "single_select"
    assert "Upper abdomen" in questions[0]["answer_options"]
    assert "Other" in questions[0]["answer_options"]
    assert meta["historical_context_used"] is False


def test_generic_yes_no_is_not_used_for_location_question():
    questions, meta = normalize_clarifying_questions(
        [
            {
                "id": "pain_place",
                "question": "Where exactly is the pain?",
                "why_needed": "The exact place can change doctor review.",
                "answer_type": "single_select",
                "answer_options": ["Yes", "No", "Don't know"],
                "allow_other": False,
                "required": True,
                "placeholder": "",
            }
        ],
        "English",
    )
    assert questions[0]["answer_type"] == "free_text"
    assert questions[0]["answer_options"] == []
    assert meta["fallback_count"] == 1


def test_real_yes_no_question_is_normalized_safely():
    questions, meta = normalize_clarifying_questions(
        [
            {
                "id": "fever",
                "question": "Do you have fever?",
                "why_needed": "Fever can change the urgency.",
                "answer_type": "yes_no",
                "answer_options": ["Yes", "No", "Not sure"],
                "allow_other": False,
                "required": True,
                "placeholder": "",
            }
        ],
        "English",
    )
    assert questions[0]["answer_type"] == "yes_no"
    assert questions[0]["answer_options"] == ["Yes", "No", "Not sure"]
    assert meta["fallback_count"] == 0
