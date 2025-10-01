from langgraph.graph import StateGraph, START, END
from langgraph.checkpoint.memory import MemorySaver
from app.state import AppState
from agents import jd_analyst, resume_fitter, applier, supervisor
from adapters.toy_jobs import toy_fetch_jobs
from adapters.toy_store import toy_record_submission

# --- Node functions (employees & boss orchestration) ---

def boss_node(state: AppState) -> AppState:
    decision = supervisor.route(state)
    if decision == "SEED":
        jobs = toy_fetch_jobs(limit=3)
        if not jobs:
            return {**state, "route": "DONE", "last_result": "No jobs found"}
        first, rest = jobs[0], jobs[1:]
        return {**state, "current_job": first, "queue": rest, "route": "JD", "last_result": "Seeded queue"}
    return {**state, "route": decision}

_jd_agent = jd_analyst.make_agent()
_resume_agent = resume_fitter.make_agent()
_apply_agent = applier.make_agent()

def jd_node(state: AppState) -> AppState:
    job = state.get("current_job")
    out = _jd_agent.invoke({"input": f"Extract requirements for {job['company']} {job['id']}. JD:\n{job['jd']}"})
    jd_summary = out.get("output", out)
    artifacts = {**state.get("artifacts", {}), "jd_summary": jd_summary}
    return {**state, "artifacts": artifacts, "last_result": f"JD summarized for {job['id']}", "route": "RESUME"}

def resume_node(state: AppState) -> AppState:
    jd_summary = state["artifacts"].get("jd_summary", {})
    edits = _resume_agent.invoke({"input": f"Propose edits.\nRESUME:\n{state['resume_md']}\n\nJD_SUMMARY:\n{jd_summary}"})
    edits_val = edits.get("output", edits)
    rendered = _resume_agent.invoke({"input": f"Render final resume.\nRESUME:\n{state['resume_md']}\nEDITS:\n{edits_val}"})
    rendered_val = rendered.get("output", rendered)
    artifacts = {**state.get("artifacts", {}), "resume_edits": edits_val, "rendered_resume": rendered_val}
    next_route = "APPROVAL" if __import__('app.config').config.HUMAN_APPROVAL_REQUIRED else "APPLY"
    return {**state, "artifacts": artifacts, "last_result": "Resume tailored", "route": next_route}

def approval_node(state: AppState) -> AppState:
    job = state["current_job"]
    jd = state["artifacts"].get("jd_summary", {})
    allow_visa = str(jd.get("visa_sponsorship", "")).lower()
    approved = ("yes" in allow_visa) or ("case" in allow_visa) or ("opt" in allow_visa)
    approvals = {**state.get("approvals", {}), job["id"]: approved}
    if not approved:
        logs = state.get("artifacts", {}).get("logs", [])
        logs.append({"job": job["id"], "decision": "rejected_by_policy"})
        artifacts = {**state.get("artifacts", {}), "logs": logs}
        q = state.get("queue", [])
        if q:
            nxt = q.pop(0)
            return {**state, "approvals": approvals, "artifacts": artifacts, "queue": q,
                    "current_job": nxt, "route": "JD", "last_result": f"{job['id']} rejected by policy"}
        return {**state, "approvals": approvals, "artifacts": artifacts, "route": "DONE",
                "last_result": f"{job['id']} rejected; queue empty"}
    return {**state, "approvals": approvals, "last_result": f"{job['id']} approved", "route": "APPLY"}

def apply_node(state: AppState) -> AppState:
    job = state["current_job"]
    jd_summary = state["artifacts"].get("jd_summary", {})
    out = _apply_agent.invoke({"input": f"Draft cover letter for job {job['id']}.\nJD_SUMMARY:\n{jd_summary}\nResume:\n{state['resume_md']}"})
    cover_val = out.get("output", out)

    payload = {
        "job_id": job["id"],
        "company": job["company"],
        "cover_letter": cover_val,
        "resume": state["artifacts"].get("rendered_resume", ""),
        "jd_summary": jd_summary
    }
    stored = toy_record_submission(job["id"], payload)

    logs = state.get("artifacts", {}).get("logs", [])
    logs.append({"job": job["id"], "action": "prepared_application", "store_result": stored})
    artifacts = {**state.get("artifacts", {}), "logs": logs, "cover_letter": cover_val}

    q = state.get("queue", [])
    if q:
        nxt = q.pop(0)
        return {**state, "artifacts": artifacts, "queue": q, "current_job": nxt,
                "route": "JD", "last_result": f"Application prepared for {job['id']}"}
    return {**state, "artifacts": artifacts, "route": "DONE", "last_result": f"All applications prepared (last: {job['id']})"}

# --- Graph builder ---

def build_graph():
    graph = StateGraph(AppState)
    graph.add_node("BOSS", boss_node)
    graph.add_node("JD", jd_node)
    graph.add_node("RESUME", resume_node)
    graph.add_node("APPROVAL", approval_node)
    graph.add_node("APPLY", apply_node)

    graph.add_edge(START, "BOSS")
    graph.add_conditional_edges("BOSS", lambda s: s["route"], {
        "JD": "JD", "RESUME": "RESUME", "APPROVAL": "APPROVAL", "APPLY": "APPLY", "DONE": END
    })
    for n in ["JD", "RESUME", "APPROVAL", "APPLY"]:
        graph.add_edge(n, "BOSS")

    return graph.compile(checkpointer=MemorySaver())