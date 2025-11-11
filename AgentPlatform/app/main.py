from app.graph import app
from app.state import AppState

def run_demo():
    print("\n" + "🚀 STARTING JOB APPLICATION WORKFLOW ".center(60, "=") + "\n")
    
    initial: AppState = {
        "user_goal": "Apply to backend jobs. Respect visa policy.",
        "current_job": None,
        "queue": [],
        "artifacts": {},
        "route": None,
        "stream_progress": False,
        "resume_text": None,
        "resume_pdf_b64": None,
    }
    
    config = {"configurable": {"thread_id": "demo-1"}}
    
    final = app.invoke(initial, config=config)
    
    print("\n" + "✨ WORKFLOW COMPLETE ".center(60, "="))
    artifacts = final.get('artifacts', {})
    print(f"\nResult: {final.get('last_result')}")
    print(f"Artifacts saved: {sorted(list(artifacts.keys()))}\n")

if __name__ == "__main__":
    run_demo()