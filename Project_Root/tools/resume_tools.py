from langchain_core.prompts import ChatPromptTemplate
from langchain_core.tools import tool
from app.config import worker_llm

@tool
def propose_resume_edits(resume_md: str, jd_summary: dict) -> dict:
    """RESUME FITTER ONLY. Propose truthful, precise resume edits aligned to JD."""
    prompt = ChatPromptTemplate.from_messages([
        ("system", "You are Resume Fitter. Output JSON list of edits {change_type, section, before, after}. No fabrication."),
        ("human", "Resume (Markdown):\n{resume}\n\nJD Summary JSON:\n{jd}\n\nPropose edits:")
    ])
    msg = prompt.format_messages(resume=resume_md, jd=jd_summary)
    return {"edits_proposal": worker_llm().invoke(msg).content}

@tool
def render_resume(resume_md: str, edits_json: str) -> dict:
    """RESUME FITTER ONLY. Render final resume (toy)."""
    return {"rendered_resume": resume_md + "\n\n<!-- Applied edits (toy): -->\n" + edits_json}
