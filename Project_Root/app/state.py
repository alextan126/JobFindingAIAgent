from typing import TypedDict, List, Literal, Optional, Dict, Any

class AppState(TypedDict):
    user_goal: str
    resume_md: str
    current_job: Optional[dict]          # {id, company, jd}
    queue: List[dict]
    artifacts: Dict[str, Any]
    route: Optional[Literal["JD","RESUME","APPROVAL","APPLY","DONE"]]
    last_result: Optional[str]
    approvals: Dict[str, bool]           # job_id -> approved