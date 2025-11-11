"""Shared helper for reporting agent progress back to the backend service."""

from __future__ import annotations

from datetime import datetime, timezone
from typing import Any, Dict, Optional

from app.config import backend_client


def maybe_report_progress(
    state: Dict[str, Any],
    *,
    stage: str,
    status: str,
    details: Optional[Dict[str, Any]] = None,
) -> None:
    """Send a progress event if streaming is enabled for the workflow."""
    if not state.get("stream_progress"):
        return

    current_job = state.get("current_job") or {}
    job_id = current_job.get("jobId") or current_job.get("id")
    company = current_job.get("company")

    message = (details or {}).get("message")
    if not message:
        job_ref = f"{company} ({job_id})" if company and job_id else company or job_id or "job"
        verb = status.replace("_", " ").capitalize()
        action = stage.replace("_", " ")
        message = f"{verb} {action} for {job_ref}"

    timestamp = (details or {}).get("timestamp")
    if timestamp is None:
        timestamp = datetime.now(timezone.utc).isoformat()

    backend_client().post_progress(
        message=message,
        stage=stage,
        timestamp=timestamp,
    )


__all__ = ["maybe_report_progress"]

