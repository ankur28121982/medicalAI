import json
import sqlite3
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path
import shutil
from typing import Any, Iterator

from app.core.config import get_settings
from app.core.crypto import encrypt_text, decrypt_text


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


def _json_dumps(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


def _json_loads(value: str | None, default: Any) -> Any:
    if not value:
        return default
    try:
        return json.loads(value)
    except Exception:
        return default


@contextmanager
def connect() -> Iterator[sqlite3.Connection]:
    settings = get_settings()
    db_path = settings.sqlite_path
    db_path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    try:
        yield conn
        conn.commit()
    finally:
        conn.close()


def _table_columns(conn: sqlite3.Connection, table_name: str) -> set[str]:
    rows = conn.execute(f"PRAGMA table_info({table_name})").fetchall()
    return {str(row["name"]) for row in rows}


def _add_column_if_missing(conn: sqlite3.Connection, table_name: str, column_name: str, column_sql: str) -> None:
    if column_name not in _table_columns(conn, table_name):
        conn.execute(f"ALTER TABLE {table_name} ADD COLUMN {column_sql}")


def init_db() -> None:
    settings = get_settings()
    settings.storage_path.mkdir(parents=True, exist_ok=True)
    (settings.storage_path / "uploads").mkdir(parents=True, exist_ok=True)
    (settings.storage_path / "reports").mkdir(parents=True, exist_ok=True)
    with connect() as conn:
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS patients (
                device_id TEXT PRIMARY KEY,
                profile_json TEXT NOT NULL,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )
            """
        )
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS reports (
                report_id TEXT PRIMARY KEY,
                device_id TEXT NOT NULL,
                original_filename TEXT NOT NULL,
                content_type TEXT NOT NULL,
                stored_path TEXT NOT NULL,
                file_sha256 TEXT NOT NULL,
                extracted_json TEXT,
                questions_json TEXT,
                analysis_json TEXT,
                status TEXT NOT NULL,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )
            """
        )
        conn.execute("CREATE INDEX IF NOT EXISTS idx_reports_device_created ON reports(device_id, created_at DESC)")
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS audit_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                request_id TEXT NOT NULL,
                endpoint TEXT NOT NULL,
                device_id TEXT,
                model_used TEXT,
                latency_ms INTEGER,
                success INTEGER NOT NULL,
                error_type TEXT,
                event_json TEXT,
                created_at TEXT NOT NULL
            )
            """
        )
        # Safe migration for older local DBs from V15/V16.
        _add_column_if_missing(conn, "audit_events", "event_json", "event_json TEXT")
        conn.execute("CREATE INDEX IF NOT EXISTS idx_audit_created ON audit_events(created_at DESC)")
        conn.execute("CREATE INDEX IF NOT EXISTS idx_audit_device_created ON audit_events(device_id, created_at DESC)")


def upsert_patient(device_id: str, profile: dict[str, Any]) -> None:
    now = _now()
    payload = encrypt_text(_json_dumps(profile))
    with connect() as conn:
        conn.execute(
            """
            INSERT INTO patients(device_id, profile_json, created_at, updated_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(device_id) DO UPDATE SET profile_json=excluded.profile_json, updated_at=excluded.updated_at
            """,
            (device_id, payload, now, now),
        )


def get_patient(device_id: str) -> dict[str, Any]:
    with connect() as conn:
        row = conn.execute("SELECT profile_json FROM patients WHERE device_id=?", (device_id,)).fetchone()
    if not row:
        return {}
    return _json_loads(decrypt_text(row["profile_json"]), {})


def create_report(*, report_id: str, device_id: str, original_filename: str, content_type: str, stored_path: str, file_sha256: str) -> None:
    now = _now()
    with connect() as conn:
        conn.execute(
            """
            INSERT INTO reports(report_id, device_id, original_filename, content_type, stored_path, file_sha256, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (report_id, device_id, original_filename, content_type, stored_path, file_sha256, "uploaded", now, now),
        )


def update_extraction(report_id: str, extracted: dict[str, Any], questions: list[dict[str, Any]], status: str = "extracted") -> None:
    now = _now()
    with connect() as conn:
        conn.execute(
            "UPDATE reports SET extracted_json=?, questions_json=?, status=?, updated_at=? WHERE report_id=?",
            (encrypt_text(_json_dumps(extracted)), encrypt_text(_json_dumps(questions)), status, now, report_id),
        )


def update_analysis(report_id: str, analysis: dict[str, Any], status: str = "analyzed") -> None:
    now = _now()
    with connect() as conn:
        conn.execute(
            "UPDATE reports SET analysis_json=?, status=?, updated_at=? WHERE report_id=?",
            (encrypt_text(_json_dumps(analysis)), status, now, report_id),
        )


def get_report(report_id: str, device_id: str | None = None) -> dict[str, Any] | None:
    with connect() as conn:
        if device_id:
            row = conn.execute("SELECT * FROM reports WHERE report_id=? AND device_id=?", (report_id, device_id)).fetchone()
        else:
            row = conn.execute("SELECT * FROM reports WHERE report_id=?", (report_id,)).fetchone()
    if not row:
        return None
    return row_to_report(row, include_payloads=True)


def list_reports(device_id: str, limit: int = 20, include_payloads: bool = False) -> list[dict[str, Any]]:
    with connect() as conn:
        rows = conn.execute(
            "SELECT * FROM reports WHERE device_id=? ORDER BY created_at DESC LIMIT ?",
            (device_id, limit),
        ).fetchall()
    return [row_to_report(row, include_payloads=include_payloads) for row in rows]


def row_to_report(row: sqlite3.Row, include_payloads: bool) -> dict[str, Any]:
    item = {
        "report_id": row["report_id"],
        "device_id": row["device_id"],
        "original_filename": row["original_filename"],
        "content_type": row["content_type"],
        "stored_path": row["stored_path"],
        "status": row["status"],
        "created_at": row["created_at"],
        "updated_at": row["updated_at"],
    }
    if include_payloads:
        item["extracted"] = _json_loads(decrypt_text(row["extracted_json"] or ""), {})
        item["questions"] = _json_loads(decrypt_text(row["questions_json"] or ""), [])
        item["analysis"] = _json_loads(decrypt_text(row["analysis_json"] or ""), {})
    else:
        analysis = _json_loads(decrypt_text(row["analysis_json"] or ""), {})
        title = analysis.get("title") or analysis.get("patient_summary", "")[:80]
        item["title"] = title or row["original_filename"]
        item["has_analysis"] = bool(row["analysis_json"])
    return item


def delete_report(report_id: str, device_id: str) -> bool:
    report = get_report(report_id, device_id)
    if not report:
        return False
    with connect() as conn:
        conn.execute("DELETE FROM reports WHERE report_id=? AND device_id=?", (report_id, device_id))
    try:
        path = Path(report["stored_path"]).resolve()
        storage_root = get_settings().storage_path.resolve()
        # Safety: only delete inside this app's storage folder.
        if str(path).startswith(str(storage_root)):
            if path.exists() and path.is_dir():
                shutil.rmtree(path)
            elif path.exists():
                path.unlink()
    except Exception:
        pass
    return True


def audit_event(
    *,
    request_id: str,
    endpoint: str,
    device_id: str | None,
    model_used: str | None,
    latency_ms: int | None,
    success: bool,
    error_type: str | None = None,
    event_json: dict[str, Any] | None = None,
) -> None:
    safe_event_json = None
    if event_json is not None:
        try:
            # Keep audit useful, but avoid accidentally storing huge/medical payloads.
            safe_event_json = _json_dumps(event_json)[:4000]
        except Exception:
            safe_event_json = _json_dumps({"event_json_error": "could_not_serialize"})
    with connect() as conn:
        conn.execute(
            """
            INSERT INTO audit_events(request_id, endpoint, device_id, model_used, latency_ms, success, error_type, event_json, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (request_id, endpoint, device_id, model_used, latency_ms, 1 if success else 0, error_type, safe_event_json, _now()),
        )
