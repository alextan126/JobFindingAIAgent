import base64

from langchain_core.tools import tool
from langgraph.prebuilt import InjectedState
from typing import Annotated
from app.config import worker_llm, backend_client
from common.pdf_utils import text_to_pdf_bytes
from tools.progress import maybe_report_progress

@tool
def generate_application(state: Annotated[dict, InjectedState]) -> str:
    """
    APPLIER ONLY. Generate cover letter and prepare submission package.
    Reads from state and writes to state.artifacts.
    """
    jd_summary = state.get('artifacts', {}).get('jd_summary', {})
    resume_text = state.get('artifacts', {}).get('rendered_resume') or state.get('resume_text', '')
    resume_pdf_b64 = state.get('artifacts', {}).get('rendered_resume_pdf_b64') or state.get('resume_pdf_b64')
    current_job = state.get('current_job', {})

    if not jd_summary:
        return "ERROR: No JD analysis found"
    if not resume_text or not resume_pdf_b64:
        return "ERROR: No resume found"

    resume_pdf_bytes = base64.b64decode(resume_pdf_b64)
    client = backend_client()

    # Generate cover letter
    letter = worker_llm().invoke(
        f"Write a 150-word cover letter for {current_job.get('company')}.\n\n"
        f"JD Summary: {jd_summary}\n\nResume:\n{resume_text}"
    ).content

    cover_letter_pdf_bytes = text_to_pdf_bytes(letter, title=f"{current_job.get('company', 'Company')} — Cover Letter")
    cover_letter_pdf_b64 = base64.b64encode(cover_letter_pdf_bytes).decode('utf-8')

    apply_url = current_job.get('applyUrl') or current_job.get('apply_url') or ''
    job_id = current_job.get('id')
    company = current_job.get('company')

    client.post_results(
        job_id=job_id,
        resume_pdf_bytes=resume_pdf_bytes,
        cover_letter_pdf_bytes=cover_letter_pdf_bytes,
        apply_url=apply_url,
    )

    payload = {
        'job_id': job_id,
        'company': company,
        'cover_letter': letter,
        'apply_url': apply_url,
        'resume_pdf_b64': resume_pdf_b64,
        'cover_letter_pdf_b64': cover_letter_pdf_b64,
    }

    # Write to state
    if 'artifacts' not in state:
        state['artifacts'] = {}
    state['artifacts']['cover_letter'] = letter
    state['artifacts']['cover_letter_pdf_b64'] = cover_letter_pdf_b64
    state['artifacts']['submission_payload'] = payload

    maybe_report_progress(
        state,
        stage="application_package",
        status="completed",
        details={
            "job_id": job_id,
            "company": company,
            "cover_letter_preview": letter[:300],
        },
    )

    return f"""Application Package Ready for {current_job.get('company')}!

COVER LETTER:
{letter}

Submission payload prepared and saved to state.artifacts.
Ready for your review and approval."""
