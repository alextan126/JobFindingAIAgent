from app.state import AppState
from app.config import HUMAN_APPROVAL_REQUIRED

# simple deterministic router; swap to LLM if desired

def route(state: AppState) -> str:
    a = state.get("artifacts", {})
    if not state.get("current_job"):
        return "SEED"
    if "jd_summary" not in a:
        return "JD"
    if "resume_edits" not in a or "rendered_resume" not in a:
        return "RESUME"
    if HUMAN_APPROVAL_REQUIRED and state["current_job"]["id"] not in state.get("approvals", {}):
        return "APPROVAL"
    if "cover_letter" not in a:
        return "APPLY"
    return "DONE"
