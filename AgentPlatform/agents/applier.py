import base64
from typing import Dict

from app.config import worker_llm
from app.service_state import service_state
from common.pdf_utils import text_to_pdf_bytes
from tools.progress import maybe_report_progress


def _record_result(job: Dict[str, str], resume_pdf_b64: str, cover_letter_pdf_b64: str, letter: str) -> None:
    apply_url = job.get("applyUrl") or job.get("apply_url") or ""
    payload = {
        "jobId": job.get("jobId") or job.get("id"),
        "company": job.get("company"),
        "resumePdfB64": resume_pdf_b64,
        "coverLetterPdfB64": cover_letter_pdf_b64,
        "applyUrl": apply_url,
        "coverLetterText": letter,
    }
    service_state.append_result(payload)


def applier_agent(state: dict) -> dict:
    """
    Custom Applier Agent - Creates application materials.
    Takes full AppState, returns updated AppState.
    """
    job = state.get("current_job", {})
    jd_summary = state.get("artifacts", {}).get("jd_summary", {})
    resume_text = state.get("artifacts", {}).get("rendered_resume") or state.get("resume_text", "")
    resume_pdf_b64 = (
        state.get("artifacts", {}).get("rendered_resume_pdf_b64") or state.get("resume_pdf_b64")
    )

    job_id = job.get("jobId") or job.get("id")
    company = job.get("company", "the company")

    if not resume_pdf_b64:
        return {**state, "last_result": "No tailored resume PDF found"}

    maybe_report_progress(
        state,
        stage="cover_letter",
        status="started",
        details={"job_id": job_id, "company": company},
    )

    print(f"\n✍️  Drafting cover letter for {company}...")

    letter = worker_llm().invoke(
        f"Write a professional 150-word cover letter for {company}.\n\n"
        f"JD Summary: {jd_summary}\n\nResume:\n{resume_text}"
    ).content

    print(f"\n📨 COVER LETTER:")
    print("=" * 60)
    print(letter)
    print("=" * 60)

    cover_letter_pdf_bytes = text_to_pdf_bytes(letter, title=f"{company} — Cover Letter")
    cover_letter_pdf_b64 = base64.b64encode(cover_letter_pdf_bytes).decode("utf-8")

    _record_result(job, resume_pdf_b64, cover_letter_pdf_b64, letter)

    artifacts = {
        **state.get("artifacts", {}),
        "cover_letter": letter,
        "cover_letter_pdf_b64": cover_letter_pdf_b64,
        "submission_payload": {
            "job_id": job_id,
            "company": company,
            "apply_url": job.get("applyUrl") or job.get("apply_url") or "",
        },
    }

    maybe_report_progress(
        state,
        stage="cover_letter",
        status="finished",
        details={"job_id": job_id, "company": company},
    )

    print(f"\n✅ Application package ready for {company}")

    return {
        **state,
        "resume_pdf_b64": resume_pdf_b64,
        "artifacts": artifacts,
        "last_result": f"Application ready for {job_id}",
    }
