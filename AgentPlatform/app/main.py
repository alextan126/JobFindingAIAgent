from app.graph import app
from app.state import AppState

def run_demo():
    print("\n" + "🚀 STARTING JOB APPLICATION WORKFLOW ".center(60, "=") + "\n")
    
    initial: AppState = {
        "user_goal": "Apply to backend jobs. Respect visa policy.",
        "resume_md": "# Alex Tan — Resume\n\n- Python, SQL, Airflow, LangChain, AWS\n- Projects: WeatherApp (FastAPI, Postgres)\n- Education: MSCS @ USF\n",
        "current_job": None,
        "queue": [],
        "artifacts": {},
        "approvals": {},
        "route": None
    }
    
    config = {"configurable": {"thread_id": "demo-1"}}
    
    final = app.invoke(initial, config=config)
    
    print("\n" + "✨ WORKFLOW COMPLETE ".center(60, "="))
    print(f"\nResult: {final.get('last_result')}")
    print(f"Approvals: {final.get('approvals', {})}")
    print(f"Artifacts: {list(final.get('artifacts', {}).keys())}\n")

if __name__ == "__main__":
    run_demo()