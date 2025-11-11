from typing import TypedDict, List, Literal, Optional, Dict, Any

class AppState(TypedDict, total=False):
    messages: list
    remaining_steps: Optional[int]
    user_goal: str
    current_job: Optional[dict]
    queue: List[dict]
    artifacts: Dict[str, Any]
    route: Optional[Literal["JD","RESUME","APPLY","DONE","HITL"]]
    last_result: Optional[str]
    approvals: Dict[str, bool]
    user_feedback: str
    last_seen_job_id: Optional[str]
    stream_progress: bool
    hitl_timeout_at: Optional[float]
    resume_text: Optional[str]
    resume_pdf_b64: Optional[str]