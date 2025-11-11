import base64

from langchain_core.prompts import ChatPromptTemplate
from langchain_core.tools import tool
from langgraph.prebuilt import InjectedState
from typing import Annotated

from app.config import worker_llm, backend_client
from common.pdf_utils import text_to_pdf_bytes
from tools.progress import maybe_report_progress

@tool
def tailor_resume(state: Annotated[dict, InjectedState]) -> str:
    """
    RESUME FITTER ONLY. Analyze JD requirements and tailor the resume.
    Reads from state and writes updated resume to state.artifacts.
    """
    job = state.get('current_job', {})
    job_id = job.get('id')
    if not job_id:
        return "ERROR: No job selected"

    resume_text = state.get('resume_text')
    resume_pdf_b64 = state.get('resume_pdf_b64')
    if not resume_text or not resume_pdf_b64:
        profile = backend_client().fetch_user_resume(job_id=job_id)
        resume_text = profile.get('resume_text', '')
        resume_pdf_b64 = profile.get('resume_pdf_b64')
        state['resume_text'] = resume_text
        state['resume_pdf_b64'] = resume_pdf_b64

    jd_summary = state.get('artifacts', {}).get('jd_summary', {})
    
    if not resume_text:
        return "ERROR: No resume found in state"
    if not jd_summary:
        return "ERROR: No JD analysis found. Run JD Analyst first."
    
    # Step 1: Propose edits
    edit_prompt = ChatPromptTemplate.from_messages([
        ("system", "Analyze resume vs JD and propose precise edits as JSON."),
        ("human", "Resume:\n{resume}\n\nJD:\n{jd}\n\nPropose edits:")
    ])
    edits = worker_llm().invoke(
        edit_prompt.format_messages(resume=resume_text, jd=jd_summary)
    ).content
    
    # Step 2: Apply edits
    render_prompt = ChatPromptTemplate.from_messages([
        ("system", "Apply edits to create updated resume in Markdown."),
        ("human", "Original:\n{resume}\n\nEdits:\n{edits}\n\nOutput updated resume:")
    ])
    rendered = worker_llm().invoke(
        render_prompt.format_messages(resume=resume_text, edits=edits)
    ).content
    
    # Write to state
    if 'artifacts' not in state:
        state['artifacts'] = {}
    state['artifacts']['resume_edits'] = edits
    state['artifacts']['rendered_resume'] = rendered

    pdf_bytes = text_to_pdf_bytes(rendered, title=f"{job.get('company', 'Company')} — Tailored Resume")
    pdf_b64 = base64.b64encode(pdf_bytes).decode('utf-8')
    state['artifacts']['rendered_resume_pdf_b64'] = pdf_b64
    state['resume_text'] = rendered
    state['resume_pdf_b64'] = pdf_b64

    backend_client().upload_tailored_resume_pdf(
        job_id=job_id,
        resume_pdf_bytes=pdf_bytes,
        metadata={"resume_text": rendered, "job_id": job_id},
    )

    maybe_report_progress(
        state,
        stage="resume_tailoring",
        status="completed",
        details={
            "resume_snapshot": rendered[:500],
            "edits": edits,
        },
    )
    
    return f"""Resume Tailoring Complete!

Key Changes Made:
{edits[:300]}...

Updated resume saved to state.artifacts['rendered_resume'] and rendered_resume_pdf_b64
You can now review it or proceed to application."""

@tool
def show_resume_diff(state: Annotated[dict, InjectedState]) -> str:
    """Show the differences between original and tailored resume."""
    original = state.get('resume_text', '')
    updated = state.get('artifacts', {}).get('rendered_resume', '')
    
    return f"""ORIGINAL RESUME:
{original}

UPDATED RESUME:
{updated}"""
