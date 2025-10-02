from langchain_openai import ChatOpenAI
from langchain_core.prompts import ChatPromptTemplate
from app.config import worker_llm
from common.models import JDSummary

def jd_analyst_agent(state: dict) -> dict:
    """
    Custom JD Analyst Agent - Analyzes job descriptions.
    Takes full AppState, returns updated AppState.
    """
    current_job = state.get('current_job', {})
    
    if not current_job:
        return {
            **state,
            "last_result": "No job to analyze"
        }
    
    job_id = current_job.get('id')
    company = current_job.get('company')
    jd_text = current_job.get('jd', '')
    
    print(f"🔍 Analyzing job requirements for {company}...")
    
    # Extract structured requirements (no reasoning - just do it)
    structured = worker_llm().with_structured_output(JDSummary)
    jd_summary = structured.invoke(
        f"Extract structured requirements (job_id={job_id}, company={company}):\n\n{jd_text}"
    ).model_dump()
    
    # Update state with analysis
    artifacts = {**state.get("artifacts", {}), "jd_summary": jd_summary}
    
    print(f"✅ Analysis complete:")
    print(f"   Title: {jd_summary.get('title')}")
    print(f"   Location: {jd_summary.get('location')}")
    print(f"   Visa: {jd_summary.get('visa_sponsorship')}")
    print(f"   Must-have: {', '.join(jd_summary.get('must_have', [])[:3])}...")
    
    return {
        **state,
        "artifacts": artifacts,
        "last_result": f"Analyzed {job_id}"
    }
