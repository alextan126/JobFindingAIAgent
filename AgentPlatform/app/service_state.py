"""Shared in-memory state for the agent FastAPI service."""

from __future__ import annotations

import copy
import threading
import time
from datetime import datetime, timezone
from typing import Any, Dict, Iterable, List, Optional, Tuple


def _canonical_job_id(job: Dict[str, Any]) -> Optional[str]:
    return job.get("jobId") or job.get("id")


class ServiceState:
    """Thread-safe container for jobs, resume bundles, progress, and results."""

    def __init__(self) -> None:
        self._lock = threading.RLock()
        self._jobs: Dict[str, Dict[str, Any]] = {}
        self._job_order: List[str] = []
        self._resume_bundle: Dict[str, Any] = {}
        self._progress_events: List[Dict[str, Any]] = []
        self._results: List[Dict[str, Any]] = []
        self._current_run: Optional[Tuple[str, str]] = None  # (run_id, job_id)

    # ------------------------------------------------------------------
    # Jobs
    # ------------------------------------------------------------------
    def load_jobs(self, jobs: Iterable[Dict[str, Any]]) -> None:
        with self._lock:
            self._jobs.clear()
            self._job_order.clear()
            for job in jobs:
                job_id = _canonical_job_id(job)
                if not job_id:
                    continue
                canonical = {
                    **job,
                    "id": job_id,
                    "jobId": job_id,
                }
                self._jobs[job_id] = canonical
                self._job_order.append(job_id)

    def upsert_job(self, job: Dict[str, Any]) -> Dict[str, Any]:
        job_id = _canonical_job_id(job)
        if not job_id:
            raise ValueError("Job payload requires 'jobId'")
        canonical = {
            **job,
            "id": job_id,
            "jobId": job_id,
        }
        with self._lock:
            if job_id not in self._job_order:
                self._job_order.append(job_id)
            self._jobs[job_id] = canonical
        return canonical

    def get_job(self, job_id: str) -> Optional[Dict[str, Any]]:
        with self._lock:
            job = self._jobs.get(job_id)
            return copy.deepcopy(job) if job else None

    def list_jobs(self) -> List[Dict[str, Any]]:
        with self._lock:
            return [copy.deepcopy(self._jobs[jid]) for jid in self._job_order if jid in self._jobs]

    # ------------------------------------------------------------------
    # Resume bundle
    # ------------------------------------------------------------------
    def set_resume_bundle(self, bundle: Dict[str, Any]) -> None:
        with self._lock:
            self._resume_bundle = dict(bundle or {})

    def get_resume_bundle(self) -> Dict[str, Any]:
        with self._lock:
            return copy.deepcopy(self._resume_bundle)

    def has_resume(self) -> bool:
        with self._lock:
            return bool(
                self._resume_bundle.get("resumePdfB64")
                or self._resume_bundle.get("resume_pdf_b64")
            )

    # ------------------------------------------------------------------
    # Progress + results
    # ------------------------------------------------------------------
    def start_run(self, run_id: str, job: Dict[str, Any], *, stream: bool) -> None:
        job_id = _canonical_job_id(job)
        if not job_id:
            raise ValueError("Job payload requires 'jobId'")
        with self._lock:
            self._current_run = (run_id, job_id)
            self._progress_events = []
        self.append_progress(
            {
                "runId": run_id,
                "jobId": job_id,
                "stage": "job_start",
                "status": "started",
                "message": f"Starting job {job_id}",
                "timestamp": datetime.now(timezone.utc).isoformat(),
                "stream": stream,
            }
        )

    def complete_run(self, *, run_id: str, status: str, error: Optional[str]) -> None:
        with self._lock:
            job_id = self._current_run[1] if self._current_run else None
            self._current_run = None
        if job_id:
            self.append_progress(
                {
                    "runId": run_id,
                    "jobId": job_id,
                    "stage": "job_complete",
                    "status": status,
                    "message": f"Job {job_id} {status}",
                    "timestamp": datetime.now(timezone.utc).isoformat(),
                    "error": error,
                }
            )

    def append_progress(self, event: Dict[str, Any]) -> None:
        payload = {
            **event,
            "timestamp": event.get("timestamp")
            or datetime.now(timezone.utc).isoformat(),
        }
        with self._lock:
            self._progress_events.append(payload)
            # Keep last 200 events
            if len(self._progress_events) > 200:
                self._progress_events = self._progress_events[-200:]

    def list_progress(self) -> List[Dict[str, Any]]:
        with self._lock:
            return [copy.deepcopy(evt) for evt in self._progress_events]

    def append_result(self, result: Dict[str, Any]) -> None:
        job_id = result.get("jobId") or result.get("job_id")
        payload = {
            **result,
            "jobId": job_id,
            "createdAt": result.get("createdAt")
            or datetime.now(timezone.utc).isoformat(),
        }
        with self._lock:
            if job_id:
                self._results = [
                    existing for existing in self._results if existing.get("jobId") != job_id
                ]
            self._results.append(payload)

    def list_results(self) -> List[Dict[str, Any]]:
        with self._lock:
            return [copy.deepcopy(item) for item in self._results]

    # ------------------------------------------------------------------
    # Reset
    # ------------------------------------------------------------------
    def reset_session(self, *, clear_jobs: bool = False) -> None:
        with self._lock:
            if clear_jobs:
                self._jobs.clear()
                self._job_order.clear()
            self._resume_bundle = {}
            self._progress_events = []
            self._results = []
            self._current_run = None


service_state = ServiceState()


__all__ = ["service_state", "ServiceState"]

