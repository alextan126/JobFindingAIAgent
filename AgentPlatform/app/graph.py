from __future__ import annotations

from typing import Any, Dict, List, Optional

from langchain_core.messages import HumanMessage
from langgraph.checkpoint.memory import MemorySaver
from langgraph.graph import END, START, StateGraph
from pydantic import BaseModel, Field

from app.backend_api import BackendAPIError
from app.config import boss_llm, backend_client
from app.state import AppState
from tools.progress import maybe_report_progress

from agents.applier import applier_agent
from agents.jd_analyst import jd_analyst_agent
from agents.resume_fitter import resume_fitter_agent


class SupervisorDecision(BaseModel):
    """Supervisor's routing decision."""

    route: str = Field(description="Next route: JD, RESUME, APPLY, HITL, or DONE")
    reasoning: str = Field(description="Brief explanation of the decision")
    user_feedback: Optional[str] = Field(default="", description="Feedback for agents")
    approve_job: Optional[bool] = Field(default=None, description="True/False for approval")
    clear_artifacts: List[str] = Field(default_factory=list, description="Artifacts to regenerate")


def _job_id(job: Optional[Dict[str, Any]]) -> Optional[str]:
    if not job:
        return None
    return job.get("jobId") or job.get("id")


def _normalize_job(job: Dict[str, Any]) -> Dict[str, Any]:
    job_id = _job_id(job)
    description = job.get("description") or job.get("jd") or ""
    return {
        **job,
        "id": job_id,
        "jobId": job_id,
        "company": job.get("company") or "Unknown company",
        "title": job.get("title"),
        "location": job.get("location"),
        "description": description,
        "jd": description,
        "applyUrl": job.get("applyUrl") or job.get("apply_url") or "",
    }


def jd_analyst_node(state: AppState) -> AppState:
    job = state.get("current_job")
    if not job:
        return {**state, "route": "DONE"}

    maybe_report_progress(
        state,
        stage="jd_analysis",
        status="started",
        details={"job_id": _job_id(job), "company": job.get("company")},
    )

    print(f"\n{'=' * 60}")
    print(f"📊 JD ANALYST - {job['company']}")
    print(f"{'=' * 60}")

    result = jd_analyst_agent(state)
    print(f"\n⬅️  Returning to SUPERVISOR")

    maybe_report_progress(
        result,
        stage="jd_analysis",
        status="finished",
        details={"job_id": _job_id(job), "company": job.get("company")},
    )

    return {**result, "route": None}


def resume_fitter_node(state: AppState) -> AppState:
    maybe_report_progress(
        state,
        stage="resume_tailoring",
        status="started",
        details={"job_id": _job_id(state.get("current_job"))},
    )

    print(f"\n{'=' * 60}")
    print(f"📝 RESUME FITTER")
    print(f"{'=' * 60}")

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
        details={"job_id": _job_id(state.get("current_job"))},
    )
    return {**result, "route": None}


def applier_node(state: AppState) -> AppState:
    maybe_report_progress(
        state,
        stage="application_package",
        status="started",
        details={"job_id": _job_id(state.get("current_job"))},
    )

    print(f"\n{'=' * 60}")
    print(f"📧 APPLIER")
    print(f"{'=' * 60}")

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
        details={"job_id": _job_id(state.get("current_job"))},
    )
    return {**result, "route": None}


def hitl_node(state: AppState) -> AppState:
    """Human-in-the-loop for displaying info and getting input."""
    job = state.get("current_job", {})
    artifacts = state.get("artifacts", {})
    messages = state.get("messages", [])

    print(f"\n{'=' * 60}")
    print(f"👤 HUMAN CONSULTATION - {job.get('company')}")
    print(f"{'=' * 60}")

    if messages and isinstance(messages[-1], HumanMessage):
        user_input = messages[-1].content.lower()

        if "resume" in user_input and any(w in user_input for w in ["show", "see", "display"]):
            print(f"\n📄 CURRENT RESUME:")
            print("=" * 60)
            print(artifacts.get("rendered_resume", "No resume yet"))
            print("=" * 60)
            return {**state, "messages": [], "route": "HITL"}

        if "cover" in user_input and any(w in user_input for w in ["show", "see", "display"]):
            print(f"\n📨 COVER LETTER:")
            print("=" * 60)
            print(artifacts.get("cover_letter", "No cover letter yet"))
            print("=" * 60)
            return {**state, "messages": [], "route": "HITL"}

    print("⏸️  Waiting for your input…")
    return {**state, "route": "HITL"}


def supervisor_node(state: AppState) -> AppState:
    """Intelligent supervisor that manages the end-to-end workflow."""
    print(f"\n🔄 SUPERVISOR - Evaluating Situation")
    client = backend_client()

    # Ensure resume bundle is present.
    resume_text = state.get("resume_text")
    resume_pdf_b64 = state.get("resume_pdf_b64")
    projects_pdf_b64 = state.get("projects_pdf_b64")

    if not resume_text or not resume_pdf_b64:
        try:
            bundle = client.fetch_resume_bundle()
        except BackendAPIError as exc:
            print(f"⚠️ Failed to fetch resume bundle: {exc}")
        else:
            resume_text = bundle.get("resume_text") or bundle.get("resumeText") or resume_text
            resume_pdf_b64 = bundle.get("resume_pdf_b64") or bundle.get("resumePdfB64") or resume_pdf_b64
            projects_pdf_b64 = (
                bundle.get("projects_pdf_b64") or bundle.get("projectsPdfB64") or projects_pdf_b64
            )
            state = {
                **state,
                "resume_text": resume_text,
                "resume_pdf_b64": resume_pdf_b64,
                "projects_pdf_b64": projects_pdf_b64,
            }

    queue: List[Dict[str, Any]] = list(state.get("queue", []))
    current_job = state.get("current_job")

    # Bootstrap job queue if empty.
    if not current_job and not queue:
        try:
            jobs = client.fetch_jobs()
        except BackendAPIError as exc:
            print(f"⚠️ Failed to fetch jobs: {exc}")
            return {**state, "route": "DONE"}

        if not jobs:
            print("📭 No jobs available from backend.")
            maybe_report_progress(state, stage="job_queue", status="empty", details={})
            return {**state, "route": "DONE"}

        normalised = [_normalize_job(job) for job in jobs if _job_id(job)]
        if not normalised:
            print("📭 No jobs after normalisation.")
            maybe_report_progress(state, stage="job_queue", status="empty", details={})
            return {**state, "route": "DONE"}

        current_job = normalised[0]
        queue = normalised[1:]

        next_state = {
            **state,
            "current_job": current_job,
            "queue": queue,
            "artifacts": {},
            "messages": [],
            "approvals": state.get("approvals", {}),
        }
        maybe_report_progress(
            next_state,
            stage="job_queue",
            status="loaded",
            details={"count": len(normalised), "job_id": _job_id(current_job)},
        )
        print(f"📥 Loaded {len(normalised)} jobs. Starting with {current_job['company']}")
        print("➡️  Routing to: JD_ANALYST")
        return {**next_state, "route": "JD"}

    # If we already have a job selected but queue has items and job was cleared.
    if not current_job and queue:
        current_job = queue.pop(0)
        state = {
            **state,
            "current_job": current_job,
            "queue": queue,
            "artifacts": {},
            "messages": [],
        }
        maybe_report_progress(
            state,
            stage="job_queue",
            status="advance",
            details={"job_id": _job_id(current_job), "company": current_job.get("company")},
        )
        print(f"➡️  Moving to next job: {current_job.get('company')}")
        return {**state, "route": "JD"}

    if not current_job:
        print("📭 No active job. Supervisor exiting.")
        return {**state, "route": "DONE"}

    artifacts = state.get("artifacts", {})
    approvals = state.get("approvals", {})
    messages = state.get("messages", [])
    job_id = _job_id(current_job)
    queue = list(state.get("queue", []))

    # Auto-complete in service mode (no human approvals).
    if artifacts.get("cover_letter_pdf_b64") and state.get("stream_progress"):
        maybe_report_progress(
            state,
            stage="job_completed",
            status="finished",
            details={"job_id": job_id, "company": current_job.get("company")},
        )
        next_state = {
            **state,
            "current_job": None,
            "artifacts": {},
            "route": "DONE",
        }
        return next_state

    # Service mode: follow deterministic flow, skip LLM + approvals.
    if state.get("stream_progress"):
        if "jd_summary" not in artifacts:
            return {**state, "route": "JD"}
        if not artifacts.get("rendered_resume") or not artifacts.get("rendered_resume_pdf_b64"):
            return {**state, "route": "RESUME"}
        if not artifacts.get("cover_letter_pdf_b64"):
            return {**state, "route": "APPLY"}

        maybe_report_progress(
            state,
            stage="job_completed",
            status="finished",
            details={"job_id": job_id, "company": current_job.get("company")},
        )
        return {
            **state,
            "current_job": None,
            "artifacts": {},
            "route": "DONE",
        }

    # Handle explicit rejections (interactive mode).
    if job_id in approvals and approvals[job_id] is False:
        if queue:
            next_job = queue.pop(0)
            next_state = {
                **state,
                "artifacts": {},
                "queue": queue,
                "current_job": next_job,
                "messages": [],
            }
            maybe_report_progress(
                next_state,
                stage="job_queue",
                status="advance",
                details={"job_id": _job_id(next_job), "company": next_job.get("company")},
            )
            print(f"❌ Job {job_id} rejected. Moving to: {next_job['company']}")
            return {**next_state, "route": "JD"}

        print(f"❌ Job {job_id} rejected. No more jobs.")
        return {**state, "route": "DONE"}

    # Handle completion.
    if job_id in approvals and approvals[job_id] and "cover_letter" in artifacts:
        if queue:
            next_job = queue.pop(0)
            next_state = {
                **state,
                "artifacts": {},
                "queue": queue,
                "current_job": next_job,
                "messages": [],
            }
            maybe_report_progress(
                next_state,
                stage="job_queue",
                status="advance",
                details={"job_id": _job_id(next_job), "company": next_job.get("company")},
            )
            print(f"✅ Job {job_id} complete! Moving to: {next_job['company']}")
            return {**next_state, "route": "JD"}

        maybe_report_progress(
            state,
            stage="job_completed",
            status="finished",
            details={"job_id": job_id, "company": current_job.get("company")},
        )
        print(f"🎉 All jobs processed!")
        return {**state, "route": "DONE"}

    has_user_input = messages and isinstance(messages[-1], HumanMessage)
    user_message = messages[-1].content if has_user_input else None

    decision_prompt = f"""You are a supervisor managing job applications.

STATUS:
- Job: {current_job.get('company')} ({job_id})
- Completed: {list(artifacts.keys())}
- Approved: {approvals.get(job_id, 'NO - not yet approved')}
- Queue: {len(queue)} more jobs

USER SAID: "{user_message if user_message else 'nothing'}"

RULES:
1. Auto-progress: JD → RESUME → APPLY (no HITL) unless user input intervenes.
2. When jd_summary, rendered_resume, and cover_letter exist AND job not approved → route to HITL to confirm.
3. Approval signals ("looks good", "approve", "proceed", "continue", "yes", "apply", "submit") → set approve_job=true and route to APPLY.
4. Rejection signals ("skip", "reject", "no", "next job") → set approve_job=false and route to JD (next job).
5. Refinement requests (user wants changes) → capture feedback, clear relevant artifacts, route accordingly.
6. If user asks to "show" something → route to HITL (display information).

Decide now:
"""

    decision = boss_llm().with_structured_output(SupervisorDecision).invoke(decision_prompt)

    print(f"🤖 Decision: {decision.reasoning}")
    print(f"➡️  Routing to: {decision.route}")

    new_state: AppState = {**state, "route": decision.route, "messages": []}

    if decision.user_feedback:
        new_state["user_feedback"] = decision.user_feedback
        print(f"💬 Feedback to agent: {decision.user_feedback[:100]}...")

    if decision.clear_artifacts:
        artifacts_copy = {**artifacts}
        for key in decision.clear_artifacts:
            artifacts_copy.pop(key, None)
        new_state["artifacts"] = artifacts_copy
        print(f"🗑️  Clearing: {decision.clear_artifacts}")

    if decision.approve_job is not None:
        new_state["approvals"] = {**approvals, job_id: decision.approve_job}
        print(f"{'✅' if decision.approve_job else '❌'} Job {job_id}: {'APPROVED' if decision.approve_job else 'REJECTED'}")

    return new_state


graph = StateGraph(AppState)

graph.add_node("supervisor", supervisor_node)
graph.add_node("jd_analyst", jd_analyst_node)
graph.add_node("resume_fitter", resume_fitter_node)
graph.add_node("applier", applier_node)
graph.add_node("hitl", hitl_node)

graph.add_edge(START, "supervisor")
graph.add_conditional_edges(
    "supervisor",
    lambda s: s.get("route"),
    {
        "JD": "jd_analyst",
        "RESUME": "resume_fitter",
        "APPLY": "applier",
        "HITL": "hitl",
        "DONE": END,
    },
)

for node in ["jd_analyst", "resume_fitter", "applier", "hitl"]:
    graph.add_edge(node, "supervisor")

app = graph.compile(checkpointer=MemorySaver())
