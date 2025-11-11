"""Backend API client (stub-ready) for job orchestration.

This module centralizes all network interactions with the backend service that
provides jobs and receives progress updates. Until real endpoints are
connected, it falls back to an in-memory stub so that the rest of the agent
workflow can be wired up without depending on legacy toy adapters.
"""

from __future__ import annotations

import base64
import json
import logging
import os
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional, Sequence

import requests


logger = logging.getLogger(__name__)


class BackendAPIError(RuntimeError):
    """Raised when the backend API call fails."""


class BackendAPI:
    """Thin client for the job backend with optional stubbed behaviour."""

    _stub_jobs: Sequence[Dict[str, Any]] = (
        {
            "jobId": "JOB-001",
            "company": "Acme Robotics",
            "title": "Backend Engineer",
            "location": "Remote (US)",
            "description": "Help build resilient APIs and data pipelines for robotics fleet management.",
            "applyUrl": "https://jobs.acmerobotics.com/apply/JOB-001",
        },
        {
            "jobId": "JOB-002",
            "company": "Nimbus Cloud",
            "title": "Data Engineer",
            "location": "San Francisco, CA",
            "description": "Own batch and streaming ingestion for petabyte-scale analytics workloads.",
            "applyUrl": "https://careers.nimbuscloud.com/jobs/JOB-002",
        },
        {
            "jobId": "JOB-003",
            "company": "Vector Labs",
            "title": "ML Engineer",
            "location": "Seattle, WA (Hybrid)",
            "description": "Productionize cutting-edge foundation models for enterprise customers.",
            "applyUrl": "https://vectorlabs.ai/jobs/JOB-003",
        },
    )

    _stub_resume_bundle = {
        "resumeText": (
            "Alex Tan\n"
            "Backend Engineer experienced with Python, SQL, Airflow, LangChain, AWS.\n"
            "Projects: WeatherApp (FastAPI, Postgres)\n"
            "Education: MSCS @ USF\n"
        ),
        "resumePdfB64": base64.b64encode(b"STUB_RESUME_PDF").decode("utf-8"),
        "projectsPdfB64": base64.b64encode(b"STUB_PROJECTS_PDF").decode("utf-8"),
    }

    def __init__(
        self,
        *,
        base_url: Optional[str] = None,
        api_key: Optional[str] = None,
        use_stub: bool = False,
        request_timeout: float = 30.0,
    ) -> None:
        self.base_url = base_url.rstrip("/") if base_url else None
        self.api_key = api_key
        self.use_stub = use_stub or not self.base_url
        self.request_timeout = request_timeout
    # ------------------------------------------------------------------ #
    # Public interface
    # ------------------------------------------------------------------ #
    def fetch_jobs(self) -> List[Dict[str, Any]]:
        """Return the list of jobs the agent should process."""
        if self.use_stub:
            return list(self._stub_jobs)

        if not self.base_url:
            raise BackendAPIError("Backend base URL is not configured.")

        url = f"{self.base_url}/api/jobs"
        response = self._request("GET", url)
        payload = response.json()
        jobs = payload.get("jobs", [])
        logger.debug("Fetched %d jobs from backend", len(jobs))
        return jobs

    def fetch_resume_bundle(self) -> Dict[str, Any]:
        """Retrieve the resume PDFs/text that the agent should use."""
        if self.use_stub:
            return dict(self._stub_resume_bundle)

        if not self.base_url:
            raise BackendAPIError("Backend base URL is not configured.")

        url = f"{self.base_url}/api/resume"
        response = self._request("GET", url)
        return response.json()

    def post_results(
        self,
        *,
        job_id: str,
        resume_pdf_bytes: bytes,
        cover_letter_pdf_bytes: bytes,
        apply_url: str,
    ) -> None:
        """Send tailored PDFs back to the frontend-facing backend."""
        if self.use_stub:
            logger.info(
                "[STUB] Results for %s -> resume=%d bytes cover_letter=%d bytes applyUrl=%s",
                job_id,
                len(resume_pdf_bytes),
                len(cover_letter_pdf_bytes),
                apply_url,
            )
            return

        if not self.base_url:
            raise BackendAPIError("Backend base URL is not configured.")

        url = f"{self.base_url}/api/results"
        payload = {
            "jobId": job_id,
            "resumePdfB64": base64.b64encode(resume_pdf_bytes).decode("utf-8"),
            "coverLetterPdfB64": base64.b64encode(cover_letter_pdf_bytes).decode("utf-8"),
            "applyUrl": apply_url,
        }
        self._request("POST", url, json=payload)

    def post_progress(
        self,
        *,
        message: str,
        stage: str,
        timestamp: Optional[str] = None,
    ) -> None:
        """Emit a progress update for the frontend progress feed."""
        if timestamp is None:
            timestamp = datetime.now(timezone.utc).isoformat()

        payload = {
            "message": message,
            "stage": stage,
            "timestamp": timestamp,
        }

        if self.use_stub:
            logger.info("[STUB] Progress: %s", json.dumps(payload))
            return

        if not self.base_url:
            raise BackendAPIError("Backend base URL is not configured.")

        url = f"{self.base_url}/api/progress"
        self._request("POST", url, json=payload)

    # ------------------------------------------------------------------ #
    # Internal helpers
    # ------------------------------------------------------------------ #
    def _request(self, method: str, url: str, **kwargs: Any) -> requests.Response:
        headers = kwargs.pop("headers", {})
        if self.api_key:
            headers["Authorization"] = f"Bearer {self.api_key}"
        kwargs["headers"] = headers
        kwargs.setdefault("timeout", self.request_timeout)

        logger.debug("Backend request %s %s (use_stub=%s)", method, url, self.use_stub)
        response = requests.request(method, url, **kwargs)
        if response.status_code >= 400:
            message = f"{method} {url} returned {response.status_code}: {response.text}"
            raise BackendAPIError(message)
        return response

def build_backend_client() -> BackendAPI:
    """Factory wired to environment variables."""
    base_url = os.getenv("BACKEND_BASE_URL")
    api_key = os.getenv("BACKEND_API_KEY")
    use_stub_env = os.getenv("BACKEND_USE_STUB", "").lower()
    use_stub = use_stub_env in {"1", "true", "yes", "on"}

    return BackendAPI(
        base_url=base_url,
        api_key=api_key,
        use_stub=use_stub,
    )


__all__ = [
    "BackendAPI",
    "BackendAPIError",
    "build_backend_client",
]

