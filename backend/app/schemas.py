from typing import Any, Literal
from pydantic import BaseModel, Field, field_validator


class PatientProfile(BaseModel):
    name_or_alias: str = Field(default="", max_length=120)
    age: str = Field(default="", max_length=20)
    sex: str = Field(default="", max_length=40)
    language: str = Field(default="auto", max_length=40)
    symptoms: str = Field(default="", max_length=2000)
    known_conditions: str = Field(default="", max_length=2000)
    current_medicines: str = Field(default="", max_length=2000)
    allergies: str = Field(default="", max_length=1000)
    pregnancy_status: str = Field(default="", max_length=200)
    notes: str = Field(default="", max_length=2000)


class AnalyzeRequest(BaseModel):
    report_id: str = Field(..., min_length=8, max_length=120)
    device_id: str = Field(..., min_length=3, max_length=160)
    language: str = Field(default="auto", max_length=40)
    patient_profile: PatientProfile = Field(default_factory=PatientProfile)
    user_answers: dict[str, str] = Field(default_factory=dict)
    extraction_corrections: str = Field(default="", max_length=10000)
    youtube_video_links: list[str] = Field(default_factory=list)
    continue_from_previous: bool = Field(default=False)
    previous_report_id: str = Field(default="", max_length=120)


class HistoryItem(BaseModel):
    report_id: str
    original_filename: str
    status: str
    created_at: str
    updated_at: str
    title: str = ""
    has_analysis: bool = False


class HistoryResponse(BaseModel):
    items: list[HistoryItem]


class DeleteResponse(BaseModel):
    ok: bool


class HealthResponse(BaseModel):
    ok: bool
    app: str


RiskLevel = Literal["Low", "Medium", "High"]


class UploadResponse(BaseModel):
    request_id: str
    report_id: str
    status: str
    safety_note: str
    extracted: dict[str, Any]
    questions: list[dict[str, Any]]
    next_step: str


class AnalyzeResponse(BaseModel):
    request_id: str
    report_id: str
    status: str
    analysis: dict[str, Any]
