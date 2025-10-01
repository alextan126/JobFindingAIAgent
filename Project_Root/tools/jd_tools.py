from langchain_core.tools import tool
from common.models import JDSummary
from app.config import worker_llm

@tool
def extract_requirements(job_id: str, company: str, job_post: str) -> dict:
    """JD ANALYST ONLY. Extract key fields from a JD into a structured JSON."""
    structured = worker_llm().with_structured_output(JDSummary)
    return structured.invoke(
        f"Extract structured requirements from this JD (job_id={job_id}, company={company}):\n\n{job_post}"
    ).model_dump()
