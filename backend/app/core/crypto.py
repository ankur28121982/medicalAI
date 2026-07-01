from cryptography.fernet import Fernet
from app.core.config import get_settings


def _fernet() -> Fernet | None:
    key = get_settings().medical_encryption_key
    if not key:
        return None
    return Fernet(key.encode("utf-8"))


def encrypt_bytes(data: bytes) -> bytes:
    f = _fernet()
    return f.encrypt(data) if f else data


def decrypt_bytes(data: bytes) -> bytes:
    f = _fernet()
    return f.decrypt(data) if f else data


def encrypt_text(text: str) -> str:
    f = _fernet()
    if not f:
        return text
    return f.encrypt(text.encode("utf-8")).decode("utf-8")


def decrypt_text(text: str) -> str:
    f = _fernet()
    if not f or not text:
        return text
    return f.decrypt(text.encode("utf-8")).decode("utf-8")
