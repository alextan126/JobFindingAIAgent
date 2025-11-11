from typing import Any, Dict, List

from langgraph.graph import StateGraph, START, END
from langgraph.checkpoint.memory import MemorySaver

from app.state import AppState
from app.backend_api import BackendAPIError
from app.config import backend_client
from tools.progress import maybe_report_progress

# Import custom agents
from agents.jd_analyst import jd_analyst_agent
from agents.resume_fitter import resume_fitter_agent
from agents.applier import applier_agent

def jd_analyst_node(state: AppState) -> AppState:
    job = state.get("current_job")
    if not job:
        return {**state, "route": "DONE", "current_job": None, "hitl_timeout_at": None}

    maybe_report_progress(
        state,
        stage="jd_analysis",
        status="started",
        details={"job_id": job.get("id"), "company": job.get("company")},
    )
    
    print(f"\n{'='*60}")
    print(f"📊 JD ANALYST - {job['company']}")
    print(f"{'='*60}")
    
    result = jd_analyst_agent(state)
    print(f"\n⬅️  Returning to SUPERVISOR")

    maybe_report_progress(
        result,
        stage="jd_analysis",
        status="finished",
        details={"job_id": job.get("id"), "company": job.get("company")},
    )
    
    return {**result, "route": None, "hitl_timeout_at": None}

def resume_fitter_node(state: AppState) -> AppState:
    maybe_report_progress(
        state,
        stage="resume_tailoring",
        status="started",
        details={"job_id": state.get("current_job", {}).get("id")},
    )

    print(f"\n{'='*60}")
    print(f"📝 RESUME FITTER")
    print(f"{'='*60}")
    
    user_feedback = state.get("user_feedback", "")
    if user_feedback:
        print(f"💬 Incorporating feedback: {user_feedback}")
    
    result = resume_fitter_agent(state)
    print(f"\n⬅️  Returning to SUPERVISOR")
    
    result["user_feedback"] = ""

    maybe_report_progress(
        result,
        stage="resume_tailoring",
        status="finished",
        details={"job_id": state.get("current_job", {}).get("id")},
    )
    return {**result, "route": None, "hitl_timeout_at": None}

def applier_node(state: AppState) -> AppState:
    maybe_report_progress(
        state,
        stage="application_package",
        status="started",
        details={"job_id": state.get("current_job", {}).get("id")},
    )

    print(f"\n{'='*60}")
    print(f"📧 APPLIER")
    print(f"{'='*60}")
    
    user_feedback = state.get("user_feedback", "")
    if user_feedback:
        print(f"💬 Incorporating feedback: {user_feedback}")
    
    result = applier_agent(state)
    print(f"\n⬅️  Returning to SUPERVISOR")
    
    result["user_feedback"] = ""

    maybe_report_progress(
        result,
        stage="application_package",
        status="finished",
        details={"job_id": state.get("current_job", {}).get("id")},
    )
    return {**result, "route": None, "hitl_timeout_at": None}

def supervisor_node(state: AppState) -> AppState:
    """Autonomous supervisor with no human-in-the-loop."""
    client = backend_client()
    print(f"\n🔄 SUPERVISOR - Evaluating Situation")

    # Ensure resume bundle is loaded once.
    if not state.get("resume_text") or not state.get("resume_pdf_b64"):
        try:
            bundle = client.fetch_resume_bundle()
        except BackendAPIError as exc:
            print(f"⚠️ Failed to fetch resume bundle: {exc}")
            return {**state, "route": "DONE"}

        resume_text = bundle.get("resume_text") or bundle.get("resumeText") or ""
        resume_pdf_b64 = bundle.get("resume_pdf_b64") or bundle.get("resumePdfB64") or ""
        projects_pdf_b64 = bundle.get("projects_pdf_b64") or bundle.get("projectsPdfB64")

        state = {
            **state,
            "resume_text": resume_text,
            "resume_pdf_b64": resume_pdf_b64,
            "projects_pdf_b64": projects_pdf_b64,
        }

    # Initialise job queue on first run.
    queue: List[Dict[str, Any]] = list(state.get("queue", []))
    current_job = state.get("current_job")

    if not current_job:
        try:
            jobs = client.fetch_jobs()
        except BackendAPIError as exc:
            print(f"⚠️ Failed to fetch jobs: {exc}")
            return {**state, "route": "DONE"}

        if not jobs:
            print("📭 No jobs available from backend.")
            client.post_progress(message="No jobs available", stage="done")
            return {**state, "route": "DONE"}

        def _normalize(job: Dict[str, Any]) -> Dict[str, Any]:
            job_id = job.get("jobId") or job.get("id")
            description = job.get("description") or ""
            return {
                **job,
                "id": job_id,
                "jobId": job_id,
                "company": job.get("company", "Unknown company"),
                "title": job.get("title"),
                "location": job.get("location"),
                "description": description,
                "jd": description,
            }

        normalised = [_normalize(job) for job in jobs]
        queue = normalised[1:]
        current_job = normalised[0]

        client.post_progress(
            message=f"Loaded {len(normalised)} jobs from backend",
            stage="job_queue",
        )

        return {
            **state,
            "current_job": current_job,
            "queue": queue,
            "artifacts": {},
            "route": "JD",
        }

    artifacts = state.get("artifacts", {})

    if "jd_summary" not in artifacts:
        return {**state, "route": "JD"}

    if not artifacts.get("rendered_resume") or not artifacts.get("rendered_resume_pdf_b64"):
        return {**state, "route": "RESUME"}

    if not artifacts.get("cover_letter_pdf_b64"):
        return {**state, "route": "APPLY"}

    # Job finished; advance to next in the queue.
    job_id = current_job.get("jobId") or current_job.get("id")
    client.post_progress(
        message=f"Completed package for {current_job.get('company', 'company')} ({job_id})",
        stage="job_completed",
    )

    if queue:
        next_job = queue.pop(0)
        client.post_progress(
            message=f"Starting next job for {next_job.get('company', 'company')} ({next_job.get('jobId')})",
            stage="job_queue",
        )
        return {
            **state,
            "current_job": next_job,
            "queue": queue,
            "artifacts": {},
            "route": "JD",
        }

    client.post_progress(message="All jobs processed", stage="done")
    print("🎉 All jobs processed.")
    return {
        **state,
        "current_job": None,
        "queue": [],
        "artifacts": {},
        "route": "DONE",
    }

# -----------------------
# Build Graph
# -----------------------
graph = StateGraph(AppState)

graph.add_node("supervisor", supervisor_node)
graph.add_node("jd_analyst", jd_analyst_node)
graph.add_node("resume_fitter", resume_fitter_node)
graph.add_node("applier", applier_node)

graph.add_edge(START, "supervisor")
graph.add_conditional_edges(
    "supervisor",
    lambda s: s.get("route"),
    {
        "JD": "jd_analyst",
        "RESUME": "resume_fitter",
        "APPLY": "applier",
        "DONE": END
    }
)

for node in ["jd_analyst", "resume_fitter", "applier"]:
    graph.add_edge(node, "supervisor")

app = graph.compile(checkpointer=MemorySaver())