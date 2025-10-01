from app.state import AppState
from app.graph import build_graph

def run_demo():
    app = build_graph()
    initial: AppState = {
        "user_goal": "Apply to 3 tech companies (backend-leaning). Respect visa policy.",
        "resume_md": "# Alex Tan — Resume\n\n- Python, SQL, Airflow, LangChain, AWS\n- Projects: WeatherApp (FastAPI, Postgres) ...\n- Education: MSCS @ USF\n",
        "current_job": None,
        "queue": [],
        "artifacts": {},
        "approvals": {}
    }
    final = app.invoke(initial)
    print("\n=== RESULT ===")
    print(final["last_result"]) 
    print("Approvals:", final.get("approvals", {}))
    print("Artifacts keys:", list(final.get("artifacts", {}).keys()))

if __name__ == "__main__":
    run_demo()
