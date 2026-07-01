from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.core.config import get_settings
from app.db import init_db
from app.routes.medical import router as medical_router
from app.schemas import HealthResponse

settings = get_settings()
app = FastAPI(
    title=settings.app_name,
    version="1.0.17",
    description="AI medical report explainer. Not a diagnosis, not a prescription, not emergency care.",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["GET", "POST", "DELETE", "OPTIONS"],
    allow_headers=["*"],
)


@app.on_event("startup")
def startup() -> None:
    init_db()


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse(ok=True, app=settings.app_name)


app.include_router(medical_router)
