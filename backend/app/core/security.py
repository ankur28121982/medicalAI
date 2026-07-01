from fastapi import Header, HTTPException, status
from app.core.config import get_settings


async def require_app_token(x_app_token: str | None = Header(default=None)) -> None:
    settings = get_settings()
    if not x_app_token or x_app_token != settings.app_access_token:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid app token")
