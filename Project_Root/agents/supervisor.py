from typing import List, Optional
from langchain_openai import ChatOpenAI
from langgraph_supervisor import create_supervisor
from app.state import AppState

# A clear, deterministic system prompt guiding how the supervisor delegates.
DEFAULT_SUPERVISOR_PROMPT = (
    "You are the Supervisor. You manage three specialists: jd_analyst, resume_fitter, and applier.
"
    "Routing policy:
"
    "- For any new job description (JD) or when artifacts.jd_summary is missing, delegate to jd_analyst.
"
    "- Once artifacts.jd_summary exists, delegate to resume_fitter to propose truthful edits and render the resume.
"
    "- Before preparing or sending application artifacts, ensure human approval has been obtained.
"
    "  If approval is not recorded in state.approvals[current_job.id], the worker should call the HITL tool
"
    "  `require_human_approval` to pause the workflow, and you should wait for the human to resume.
"
    "- After approval (or if not required), delegate to applier to generate a cover letter and a submission payload.
"
    "- Do not fabricate facts. Prefer concise, structured outputs in artifacts. Respect the user's instructions and policy gates.
"
    "State usage guidance:
"
    "- Messages: maintained automatically by the runtime.
"
    "- artifacts: store jd_summary, resume_edits, rendered_resume, cover_letter, and logs.
"
    "- approvals: map of job_id -> boolean indicating human approval.
"
    "- queue/current_job: if multiple jobs are present, workers may update these keys; you may prompt them to move to the next job when finished.
"
)


def build_supervisor(
    agents: List, *,
    model: Optional[ChatOpenAI] = None,
    prompt: Optional[str] = None,
):
    """
    Create a prebuilt Supervisor that coordinates ReAct workers while preserving custom state.

    Parameters
    ----------
    agents: List
        A list of prebuilt worker agents (e.g., jd_analyst.agent, resume_fitter.agent, applier.agent).
    model: Optional[ChatOpenAI]
        The LLM used by the supervisor for delegation. Defaults to ChatOpenAI(gpt-4o, temperature=0).
    prompt: Optional[str]
        Override the default routing prompt if you need custom behavior.

    Returns
    -------
    workflow : StateGraph
        A LangGraph workflow (uncompiled). Call .compile(checkpointer=...) in app/graph.py.
    """
    if model is None:
        model = ChatOpenAI(model="gpt-4o", temperature=0)

    supervisor_workflow = create_supervisor(
        agents=agents,
        model=model,
        prompt=prompt or DEFAULT_SUPERVISOR_PROMPT,
        state_schema=AppState,  # <-- keeps messages plus artifacts/queue/approvals
    )
    return supervisor_workflow