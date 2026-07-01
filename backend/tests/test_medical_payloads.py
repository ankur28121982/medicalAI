from app.schemas import AnalyzeRequest, PatientProfile


def test_analyze_request_validates():
    req = AnalyzeRequest(report_id="12345678-abcd", device_id="device-1", patient_profile=PatientProfile(age="42"))
    assert req.device_id == "device-1"
    assert req.patient_profile.age == "42"
