from app.config import worker_llm

def applier_agent(state: dict) -> dict:
    """
    Custom Applier Agent - Creates application materials.
    Takes full AppState, returns updated AppState.
    """
    job = state.get("current_job", {})
    jd_summary = state.get("artifacts", {}).get("jd_summary", {})
    resume = state.get("artifacts", {}).get("rendered_resume", state.get("resume_md", ""))
    
    company = job.get('company')
    
    print(f"\n✍️  Drafting cover letter for {company}...")
    
    # Generate cover letter
    letter = worker_llm().invoke(
        f"Write a professional 150-word cover letter for {company}.\n\n"
        f"JD Summary: {jd_summary}\n\nResume:\n{resume}"
    ).content
    
    print(f"\n📨 COVER LETTER:")
    print("="*60)
    print(letter)
    print("="*60)
    
    # Prepare submission
    payload = {
        'job_id': job.get('id'),
        'company': company,
        'cover_letter': letter,
        'resume': resume
    }
    
    artifacts = {
        **state.get("artifacts", {}),
        "cover_letter": letter,
        "submission_payload": payload
    }
    
    print(f"\n✅ Application package ready for {company}")
    
    return {
        **state,
        "artifacts": artifacts,
        "last_result": f"Application ready for {job.get('id')}"
    }

