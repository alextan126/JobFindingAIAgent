from langchain_core.tools import tool
from langgraph.prebuilt import InjectedState
from typing import Annotated
from common.models import JDSummary
from app.config import worker_llm
from tools.progress import maybe_report_progress

@tool
def analyze_current_job(state: Annotated[dict, InjectedState]) -> str:
    """
    JD ANALYST ONLY. Analyze the job description from state.current_job.
    Returns a formatted summary of the job requirements.
    """
    current_job = state.get('current_job', {})
    
    if not current_job:
        return "ERROR: No job found in state"
    
    job_id = current_job.get('id', 'unknown')
    company = current_job.get('company', 'unknown')
    job_post = (
        current_job.get('jd')
        or current_job.get('job_description')
        or current_job.get('description')
        or current_job.get('text')
        or ''
    )
    
    if not job_post:
        return "ERROR: Job description is empty"
    
    # Extract structured data
    structured = worker_llm().with_structured_output(JDSummary)
    jd_summary = structured.invoke(
        f"Extract structured requirements from this JD (job_id={job_id}, company={company}):\n\n{job_post}"
    ).model_dump()
    
    # Write to state
    if 'artifacts' not in state:
        state['artifacts'] = {}
    state['artifacts']['jd_summary'] = jd_summary
    
    # Return formatted summary for agent
    maybe_report_progress(
        state,
        stage="jd_analysis",
        status="completed",
        details={
            "job_id": job_id,
            "company": company,
            "summary": jd_summary,
        },
    )

    return f"""Job Analysis Complete for {company}!

Job ID: {job_id}
Title: {jd_summary.get('title', 'N/A')}
Location: {jd_summary.get('location', 'N/A')}
Salary: {jd_summary.get('salary', 'N/A')}
Visa: {jd_summary.get('visa_sponsorship', 'N/A')}

Must-Have Skills: {', '.join(jd_summary.get('must_have', []))}
Nice-to-Have: {', '.join(jd_summary.get('nice_to_have', []))}

Analysis saved to state.artifacts['jd_summary']"""
