from langchain_core.tools import tool
from app.config import worker_llm

@tool
def generate_cover_letter(jd_summary: dict, resume_md: str) -> dict:
    """APPLIER ONLY. Draft a short, sincere cover letter tailored to JD."""
    letter = worker_llm().invoke(
        "Write a 150-180 word cover letter tailored to this JD summary. "
        "Be sincere and only use facts in the resume.\n\n"
        f"JD Summary: {jd_summary}\n\nResume:\n{resume_md}"
    ).content
    return {"cover_letter": letter}
