from langchain_core.prompts import ChatPromptTemplate
from langchain_core.tools import tool
from langgraph.prebuilt import InjectedState
from typing import Annotated
from app.config import worker_llm

@tool
def tailor_resume(state: Annotated[dict, InjectedState]) -> str:
    """
    RESUME FITTER ONLY. Analyze JD requirements and tailor the resume.
    Reads from state and writes updated resume to state.artifacts.
    """
    resume_md = state.get('resume_md', '')
    jd_summary = state.get('artifacts', {}).get('jd_summary', {})
    
    if not resume_md:
        return "ERROR: No resume found in state"
    if not jd_summary:
        return "ERROR: No JD analysis found. Run JD Analyst first."
    
    # Step 1: Propose edits
    edit_prompt = ChatPromptTemplate.from_messages([
        ("system", "Analyze resume vs JD and propose precise edits as JSON."),
        ("human", "Resume:\n{resume}\n\nJD:\n{jd}\n\nPropose edits:")
    ])
    edits = worker_llm().invoke(
        edit_prompt.format_messages(resume=resume_md, jd=jd_summary)
    ).content
    
    # Step 2: Apply edits
    render_prompt = ChatPromptTemplate.from_messages([
        ("system", "Apply edits to create updated resume in Markdown."),
        ("human", "Original:\n{resume}\n\nEdits:\n{edits}\n\nOutput updated resume:")
    ])
    rendered = worker_llm().invoke(
        render_prompt.format_messages(resume=resume_md, edits=edits)
    ).content
    
    # Write to state
    if 'artifacts' not in state:
        state['artifacts'] = {}
    state['artifacts']['resume_edits'] = edits
    state['artifacts']['rendered_resume'] = rendered
    
    return f"""Resume Tailoring Complete!

Key Changes Made:
{edits[:300]}...

Updated resume saved to state.artifacts['rendered_resume']
You can now review it or proceed to application."""

@tool
def show_resume_diff(state: Annotated[dict, InjectedState]) -> str:
    """Show the differences between original and tailored resume."""
    original = state.get('resume_md', '')
    updated = state.get('artifacts', {}).get('rendered_resume', '')
    
    return f"""ORIGINAL RESUME:
{original}

UPDATED RESUME:
{updated}"""
