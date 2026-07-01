from functools import lru_cache
from pathlib import Path
from pydantic import Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    app_env: str = "local"
    app_name: str = "Medical Report AI"
    app_access_token: str = Field(default="change-this-long-random-token")
    public_base_url: str = "http://127.0.0.1:8000"

    openai_api_key: str = ""
    openai_extraction_model: str = "gpt-5.4-mini"
    openai_medical_model: str = "gpt-5.5"
    openai_safety_model: str = "gpt-5.4-mini"
    openai_reasoning_effort: str = "medium"

    database_url: str = "sqlite:///./storage/medical_report_ai.db"
    storage_dir: str = "./storage"
    max_upload_mb: int = 12
    max_history_items: int = 8

    medical_encryption_key: str = ""
    require_encryption: bool = False

    rate_limit_per_minute: int = 8

    @property
    def sqlite_path(self) -> Path:
        if not self.database_url.startswith("sqlite:///"):
            raise ValueError("Only sqlite:/// DATABASE_URL is supported in this starter. Use Postgres adapter before large production scale.")
        return Path(self.database_url.replace("sqlite:///", "")).resolve()

    @property
    def storage_path(self) -> Path:
        return Path(self.storage_dir).resolve()

    @field_validator("openai_reasoning_effort")
    @classmethod
    def valid_reasoning(cls, value: str) -> str:
        value = (value or "medium").lower().strip()
        if value not in {"none", "low", "medium", "high", "xhigh"}:
            return "medium"
        return value


@lru_cache
def get_settings() -> Settings:
    settings = Settings()
    if settings.app_env.lower() in {"prod", "production"}:
        if settings.app_access_token == "change-this-long-random-token":
            raise RuntimeError("APP_ACCESS_TOKEN must be changed in production")
        if settings.require_encryption and not settings.medical_encryption_key:
            raise RuntimeError("MEDICAL_ENCRYPTION_KEY is required when REQUIRE_ENCRYPTION=true")
        if not settings.openai_api_key:
            raise RuntimeError("OPENAI_API_KEY is required in production")
    return settings
