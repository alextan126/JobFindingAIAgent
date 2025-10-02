from langchain_core.tools import tool
from langgraph.prebuilt import InjectedState
from typing import Annotated
from app.config import worker_llm

@tool
def generate_application(state: Annotated[dict, InjectedState]) -> str:
    """
    APPLIER ONLY. Generate cover letter and prepare submission package.
    Reads from state and writes to state.artifacts.
    """
    jd_summary = state.get('artifacts', {}).get('jd_summary', {})
    resume_md = state.get('artifacts', {}).get('rendered_resume') or state.get('resume_md', '')
    current_job = state.get('current_job', {})
    
    if not jd_summary:
        return "ERROR: No JD analysis found"
    if not resume_md:
        return "ERROR: No resume found"
    
    # Generate cover letter
    letter = worker_llm().invoke(
        f"Write a 150-word cover letter for {current_job.get('company')}.\n\n"
        f"JD Summary: {jd_summary}\n\nResume:\n{resume_md}"
    ).content
    
    # Prepare payload
    payload = {
        'job_id': current_job.get('id'),
        'company': current_job.get('company'),
        'cover_letter': letter,
        'resume': resume_md
    }
    
    # Write to state
    if 'artifacts' not in state:
        state['artifacts'] = {}
    state['artifacts']['cover_letter'] = letter
    state['artifacts']['submission_payload'] = payload
    
    return f"""Application Package Ready for {current_job.get('company')}!

COVER LETTER:
{letter}

Submission payload prepared and saved to state.artifacts.
Ready for your review and approval."""
