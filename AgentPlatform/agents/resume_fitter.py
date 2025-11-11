import base64

from langchain_core.prompts import ChatPromptTemplate

from app.config import worker_llm
from common.pdf_utils import text_to_pdf_bytes

def resume_fitter_agent(state: dict) -> dict:
    """
    Custom Resume Fitter Agent - Tailors resumes to JDs.
    Takes full AppState, returns updated AppState.
    """
    jd_summary = state.get("artifacts", {}).get("jd_summary", {})
    job = state.get("current_job", {})

    resume_text = state.get("resume_text") or ""
    if not resume_text:
        return {**state, "last_result": "No resume found"}
    
    if not jd_summary:
        return {**state, "last_result": "No JD analysis found"}
    
    print(f"\n📄 ORIGINAL RESUME:")
    print("="*60)
    print(resume_text or "[Empty resume]")
    print("="*60)
    
    print(f"\n✏️  Tailoring resume to match job requirements...")
    
    # Propose edits
    edit_prompt = ChatPromptTemplate.from_messages([
        ("system", "You are a resume editor. Propose precise, truthful edits as JSON."),
        ("human", "Resume:\n{resume}\n\nJob Requirements:\n{jd}\n\nPropose edits:")
    ])
    edits = worker_llm().invoke(
        edit_prompt.format_messages(resume=resume_text, jd=jd_summary)
    ).content
    
    # Apply edits
    render_prompt = ChatPromptTemplate.from_messages([
        ("system", "Apply edits to create updated resume in Markdown."),
        ("human", "Original:\n{resume}\n\nEdits:\n{edits}\n\nOutput updated resume:")
    ])
    rendered = worker_llm().invoke(
        render_prompt.format_messages(resume=resume_text, edits=edits)
    ).content
    
    print(f"\n💡 PROPOSED EDITS:")
    print("="*60)
    print(edits)
    print("="*60)
    
    print(f"\n✨ UPDATED RESUME:")
    print("="*60)
    print(rendered)
    print("="*60)
    
    pdf_bytes = text_to_pdf_bytes(rendered, title=f"{job.get('company', 'Company')} — Tailored Resume")
    pdf_b64 = base64.b64encode(pdf_bytes).decode("utf-8")

    # Update state
    artifacts = {
        **state.get("artifacts", {}),
        "resume_edits": edits,
        "rendered_resume": rendered,
        "rendered_resume_pdf_b64": pdf_b64,
    }
    
    print(f"\n✅ Resume tailored")
    
    return {
        **state,
        "resume_text": rendered,
        "resume_pdf_b64": pdf_b64,
        "projects_pdf_b64": state.get("projects_pdf_b64"),
        "artifacts": artifacts,
        "last_result": "Resume tailored"
    }