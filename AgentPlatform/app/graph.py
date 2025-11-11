from typing import Any, Dict

from langgraph.graph import StateGraph, START, END
from langgraph.checkpoint.memory import MemorySaver

from app.state import AppState
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
    print(f"\n🔄 SUPERVISOR - Evaluating Situation")

    current_job = state.get("current_job")

    if not current_job:
        print("📭 No active job. Supervisor exiting.")
        return {**state, "route": "DONE"}

    artifacts = state.get("artifacts", {})

    if "jd_summary" not in artifacts:
        return {**state, "route": "JD"}

    if not artifacts.get("rendered_resume") or not artifacts.get("rendered_resume_pdf_b64"):
        return {**state, "route": "RESUME"}

    if not artifacts.get("cover_letter_pdf_b64"):
        return {**state, "route": "APPLY"}

    job_id = current_job.get("jobId") or current_job.get("id")
    maybe_report_progress(
        state,
        stage="job_completed",
        status="finished",
        details={"job_id": job_id, "company": current_job.get("company")},
    )
    print("🎉 Job processed.")
    return {
        **state,
        "current_job": None,
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