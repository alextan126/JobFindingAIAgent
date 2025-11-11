import base64

from app.config import worker_llm, backend_client
from common.pdf_utils import text_to_pdf_bytes

def applier_agent(state: dict) -> dict:
    """
    Custom Applier Agent - Creates application materials.
    Takes full AppState, returns updated AppState.
    """
    job = state.get("current_job", {})
    jd_summary = state.get("artifacts", {}).get("jd_summary", {})
    resume_text = state.get("artifacts", {}).get("rendered_resume") or state.get("resume_text", "")
    resume_pdf_b64 = state.get("artifacts", {}).get("rendered_resume_pdf_b64") or state.get("resume_pdf_b64")

    job_id = job.get('id')
    company = job.get('company')

    if not resume_pdf_b64:
        return {**state, "last_result": "No tailored resume PDF found"}

    resume_pdf_bytes = base64.b64decode(resume_pdf_b64)
    
    client = backend_client()

    print(f"\n✍️  Drafting cover letter for {company}...")
    
    # Generate cover letter
    letter = worker_llm().invoke(
        f"Write a professional 150-word cover letter for {company}.\n\n"
        f"JD Summary: {jd_summary}\n\nResume:\n{resume_text}"
    ).content
    
    print(f"\n📨 COVER LETTER:")
    print("="*60)
    print(letter)
    print("="*60)
    
    cover_letter_pdf_bytes = text_to_pdf_bytes(letter, title=f"{company} — Cover Letter")
    cover_letter_pdf_b64 = base64.b64encode(cover_letter_pdf_bytes).decode("utf-8")

    apply_url = job.get("applyUrl") or job.get("apply_url") or ""

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
    
    artifacts = {
        **state.get("artifacts", {}),
        "cover_letter": letter,
        "cover_letter_pdf_b64": cover_letter_pdf_b64,
        "submission_payload": payload
    }
    
    print(f"\n✅ Application package ready for {company}")
    
    return {
        **state,
        "resume_pdf_b64": resume_pdf_b64,
        "artifacts": artifacts,
        "last_result": f"Application ready for {job_id}"
    }

