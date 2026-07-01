import time
from collections import defaultdict, deque
from fastapi import HTTPException, Request, status
from app.core.config import get_settings

_BUCKETS: dict[str, deque[float]] = defaultdict(deque)


def check_rate_limit(request: Request, device_id: str | None = None) -> None:
    settings = get_settings()
    identity = device_id or request.client.host if request.client else "unknown"
    now = time.time()
    bucket = _BUCKETS[identity]
    while bucket and now - bucket[0] > 60:
        bucket.popleft()
    if len(bucket) >= settings.rate_limit_per_minute:
        raise HTTPException(status_code=status.HTTP_429_TOO_MANY_REQUESTS, detail="Too many requests. Please try again after a minute.")
    bucket.append(now)
