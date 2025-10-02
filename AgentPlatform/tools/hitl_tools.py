from langchain_core.tools import tool
from langgraph.prebuilt import InjectedState
from langgraph.types import interrupt
from typing import Annotated

@tool
def require_human_approval(
    state: Annotated[dict, InjectedState],
    context: str
) -> str:
    """
    Human-in-the-loop gate. Pauses the graph until you resume
    by sending a user message (same thread) after reviewing.
    Records the approval request in state for tracking.
    """
    current_job = state.get('current_job', {})
    job_id = current_job.get('id', 'unknown')
    
    # Log the approval request in artifacts
    if 'artifacts' not in state:
        state['artifacts'] = {}
    if 'approval_logs' not in state['artifacts']:
        state['artifacts']['approval_logs'] = []
    
    state['artifacts']['approval_logs'].append({
        'job_id': job_id,
        'context': context,
        'status': 'pending'
    })
    
    # Interrupt the workflow
    interrupt({"type": "approval", "context": context, "job_id": job_id})
    
    # When resumed, mark as approved
    if 'approvals' not in state:
        state['approvals'] = {}
    state['approvals'][job_id] = True
    
    return f"Approval granted for job {job_id}. Continuing workflow."