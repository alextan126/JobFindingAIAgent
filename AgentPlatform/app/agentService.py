import argparse
import threading
import time
import uuid
from typing import Any, Dict, Optional

import uvicorn
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from app.backend_api import BackendAPIError
from app.config import backend_client
from app.graph import app as langgraph_app
from app.service_state import service_state
from app.state import AppState


class ResumeUploadRequest(BaseModel):
    resume_pdf_b64: Optional[str] = Field(None, alias="resumePdfB64")
    projects_pdf_b64: Optional[str] = Field(None, alias="projectsPdfB64")
    resume_text: Optional[str] = Field(None, alias="resumeText")

    class Config:
        allow_population_by_field_name = True
        extra = "ignore"


class ResumeUploadResponse(BaseModel):
    stored: bool = True


class NextJobOptions(BaseModel):
    stream: bool = True
    thread_id: Optional[str] = Field(None, alias="threadId")

    class Config:
        allow_population_by_field_name = True
        extra = "ignore"


class NextJobRequest(BaseModel):
    job_id: Optional[str] = Field(None, alias="jobId")
    job: Optional[Dict[str, Any]] = None
    options: NextJobOptions = NextJobOptions()

    class Config:
        allow_population_by_field_name = True
        extra = "ignore"


class ResetRequest(BaseModel):
    reload_jobs: bool = Field(True, alias="reloadJobs")
    clear_jobs: bool = Field(False, alias="clearJobs")
    clear_resume: bool = Field(True, alias="clearResume")

    class Config:
        allow_population_by_field_name = True
        extra = "ignore"


def _canonical_job(job: Dict[str, Any]) -> Dict[str, Any]:
    job_id = job.get("jobId") or job.get("id")
    if not job_id:
        raise ValueError("Job payload must include 'jobId'")
    description = job.get("description") or job.get("jd") or ""
    return {
        **job,
        "id": job_id,
        "jobId": job_id,
        "description": description,
        "jd": description,
    }


class AgentRunner:
    """Manages lifecycle of the LangGraph workflow for the HTTP service."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._thread: Optional[threading.Thread] = None
        self._current_run_id: Optional[str] = None
        self._current_job: Optional[Dict[str, Any]] = None
        self._last_result: Dict[str, Any] = {
            "status": "idle",
            "runId": None,
            "job": None,
            "startedAt": None,
            "completedAt": None,
            "error": None,
            "summary": None,
        }

    def _run_agent(
        self,
        *,
        run_id: str,
        job: Dict[str, Any],
        stream: bool,
        thread_id: Optional[str],
    ) -> None:
        start_time = time.time()
        status = "succeeded"
        error: Optional[str] = None
        summary: Optional[Dict[str, Any]] = None
        try:
            state = execute_agent(job=job, stream=stream, thread_id=thread_id or run_id)
            summary = {
                "approvals": state.get("approvals", {}),
                "artifacts_keys": list((state.get("artifacts") or {}).keys()),
            }
        except Exception as exc:  # pragma: no cover - defensive logging
            status = "failed"
            error = str(exc)
        finally:
            completed_at = time.time()
            service_state.complete_run(run_id=run_id, status=status, error=error)
            with self._lock:
                self._last_result = {
                    "status": status,
                    "runId": run_id,
                    "job": self._current_job,
                    "startedAt": start_time,
                    "completedAt": completed_at,
                    "error": error,
                    "summary": summary,
                }
                self._thread = None
                self._current_run_id = None
                self._current_job = None

    def start(self, *, job: Dict[str, Any], stream: bool, thread_id: Optional[str]) -> str:
        with self._lock:
            if self._thread and self._thread.is_alive():
                raise RuntimeError("Agent run already in progress")

            run_id = str(uuid.uuid4())
            canonical_job = _canonical_job(job)
            self._current_job = canonical_job
            self._current_run_id = run_id
            self._last_result = {
                "status": "running",
                "runId": run_id,
                "job": canonical_job,
                "startedAt": time.time(),
                "completedAt": None,
                "error": None,
                "summary": None,
            }

            service_state.start_run(run_id, canonical_job, stream=stream)

            self._thread = threading.Thread(
                target=self._run_agent,
                kwargs={
                    "run_id": run_id,
                    "job": canonical_job,
                    "stream": stream,
                    "thread_id": thread_id,
                },
                daemon=True,
            )
            self._thread.start()

            return run_id

    def status(self) -> Dict[str, Any]:
        with self._lock:
            status = dict(self._last_result)
            if self._thread and self._thread.is_alive():
                status["status"] = "running"
                status["runId"] = self._current_run_id
            return status


def execute_agent(*, job: Dict[str, Any], stream: bool, thread_id: Optional[str]) -> AppState:
    """Execute the LangGraph workflow synchronously."""
    resume_bundle = service_state.get_resume_bundle()
    resume_text = resume_bundle.get("resumeText") or resume_bundle.get("resume_text")
    resume_pdf_b64 = resume_bundle.get("resumePdfB64") or resume_bundle.get("resume_pdf_b64")
    projects_pdf_b64 = resume_bundle.get("projectsPdfB64") or resume_bundle.get("projects_pdf_b64")

    state: AppState = {
        "user_goal": "Apply to backend jobs. Respect visa policy.",
        "current_job": job,
        "queue": [],
        "artifacts": {},
        "approvals": {},
        "route": None,
        "stream_progress": stream,
        "resume_text": resume_text,
        "resume_pdf_b64": resume_pdf_b64,
        "projects_pdf_b64": projects_pdf_b64,
    }

    config = {"configurable": {"thread_id": thread_id or f"job-{job.get('jobId')}"}}

    while True:
        for event in langgraph_app.stream(state, config=config, stream_mode="values"):
            state = event
            if state.get("route") == "DONE":
                break

        if state.get("route") == "DONE":
            break

    return state


def _bootstrap_from_backend() -> None:
    client = backend_client()
    try:
        jobs = client.fetch_jobs()
        if jobs:
            service_state.load_jobs(jobs)
            print(f"🗂️  Loaded {len(jobs)} jobs from backend bootstrap.")
    except BackendAPIError as exc:
        print(f"⚠️ Failed to bootstrap jobs: {exc}")

    try:
        bundle = client.fetch_resume_bundle()
        if bundle:
            service_state.set_resume_bundle(bundle)
            print("📄 Bootstrap resume bundle loaded.")
    except BackendAPIError as exc:
        print(f"⚠️ Failed to bootstrap resume bundle: {exc}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Expose the LangGraph job application workflow as an HTTP service."
    )
    parser.add_argument("--host", default="0.0.0.0", help="Interface to bind.")
    parser.add_argument("--port", type=int, default=7070, help="Port to bind.")
    parser.add_argument(
        "--reload",
        action="store_true",
        help="Enable FastAPI reload (development only).",
    )
    return parser.parse_args()


runner = AgentRunner()
api = FastAPI(title="Agent Workflow Service", version="2.0.0")
api.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@api.on_event("startup")
def _startup() -> None:
    _bootstrap_from_backend()


@api.get("/health")
def health_check() -> Dict[str, str]:
    return {"status": "ok"}


@api.get("/api/jobs")
def list_jobs() -> Dict[str, Any]:
    return {"jobs": service_state.list_jobs()}


@api.post("/api/uploadResume")
def upload_resume(payload: ResumeUploadRequest) -> ResumeUploadResponse:
    bundle = {
        "resumePdfB64": payload.resume_pdf_b64,
        "projectsPdfB64": payload.projects_pdf_b64,
        "resumeText": payload.resume_text,
    }
    service_state.set_resume_bundle(bundle)
    return ResumeUploadResponse()


@api.get("/api/resume")
def get_resume() -> Dict[str, Any]:
    return {"resume": service_state.get_resume_bundle()}


@api.post("/api/nextJob")
def trigger_next_job(request: NextJobRequest) -> Dict[str, Any]:
    if request.job is None and request.job_id is None:
        raise HTTPException(status_code=400, detail="Provide job payload or jobId")

    if request.job:
        job = service_state.upsert_job(request.job)
    else:
        job = service_state.get_job(request.job_id or "")
        if not job:
            raise HTTPException(status_code=404, detail="Unknown jobId")

    if not service_state.has_resume():
        raise HTTPException(
            status_code=400,
            detail="Resume bundle not loaded. Upload via /api/uploadResume first.",
        )

    try:
        run_id = runner.start(
            job=job,
            stream=request.options.stream,
            thread_id=request.options.thread_id,
        )
    except RuntimeError as exc:
        raise HTTPException(status_code=409, detail=str(exc))

    return {"runId": run_id, "job": job}


@api.get("/api/status")
def get_status() -> Dict[str, Any]:
    return runner.status()


@api.get("/api/progress")
def get_progress() -> Dict[str, Any]:
    return {"events": service_state.list_progress()}


@api.get("/api/results")
def get_results() -> Dict[str, Any]:
    return {"results": service_state.list_results()}


@api.post("/api/reset")
def reset_service(request: ResetRequest = ResetRequest()) -> Dict[str, Any]:
    preserved_resume = service_state.get_resume_bundle() if not request.clear_resume else {}

    service_state.reset_session(clear_jobs=request.clear_jobs)

    if request.reload_jobs:
        _bootstrap_from_backend()
    elif request.clear_jobs:
        service_state.load_jobs([])

    if not request.clear_resume and preserved_resume:
        service_state.set_resume_bundle(preserved_resume)

    return {"cleared": True}


if __name__ == "__main__":
    args = parse_args()
    uvicorn.run(
        "app.agentService:api",
        host=args.host,
        port=args.port,
        reload=args.reload,
    )