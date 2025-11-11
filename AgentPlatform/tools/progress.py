"""Shared helper for reporting agent progress to the FastAPI service."""

from __future__ import annotations

from datetime import datetime, timezone
from typing import Any, Dict, Optional

from app.service_state import service_state


def maybe_report_progress(
    state: Dict[str, Any],
    *,
    stage: str,
    status: str,
    details: Optional[Dict[str, Any]] = None,
) -> None:
    """Record a progress event for the current job."""
    if not state.get("stream_progress"):
        return

    current_job = state.get("current_job") or {}
    job_id = current_job.get("jobId") or current_job.get("id")
    company = current_job.get("company")

    details = details or {}

    message = details.get("message")
    if not message:
        job_ref = f"{company} ({job_id})" if company and job_id else company or job_id or "job"
        verb = status.replace("_", " ").capitalize()
        action = stage.replace("_", " ")
        message = f"{verb} {action} for {job_ref}"

    timestamp = details.get("timestamp")
    if timestamp is None:
        timestamp = datetime.now(timezone.utc).isoformat()

    service_state.append_progress(
        {
            "message": message,
            "stage": stage,
            "status": status,
            "timestamp": timestamp,
            "jobId": job_id,
            "company": company,
            "details": {k: v for k, v in details.items() if k not in {"message", "timestamp"}},
        }
    )


__all__ = ["maybe_report_progress"]

