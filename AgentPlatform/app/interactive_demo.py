import argparse

from app.graph import app
from app.state import AppState


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run the conversational job application workflow."
    )
    parser.add_argument(
        "--stream",
        action="store_true",
        help="Stream intermediate progress events to the backend.",
    )
    return parser.parse_args()


def run_conversational(*, stream: bool = False):
    """Run the automated job-application workflow."""
    print("\n" + "="*70)
    print("  🚀 JOB APPLICATION AGENT (AUTO MODE)".center(70))
    print("="*70)
    print("\nThe agent will process the available jobs end-to-end with no manual input.")
    print("Use --stream to forward progress updates to the frontend backend.\n")
    
    state: AppState = {
        "user_goal": "Apply to backend jobs. Respect visa policy.",
        "current_job": None,
        "queue": [],
        "artifacts": {},
        "route": None,
        "stream_progress": stream,
        "resume_text": None,
        "resume_pdf_b64": None,
    }
    
    config = {"configurable": {"thread_id": "conv-demo"}}
    
    while True:
        # Stream events
        for event in app.stream(state, config=config, stream_mode="values"):
            state = event
            
            # Check if done
            if state.get('route') == 'DONE':
                break
        
        if state.get('route') == 'DONE':
            break
    
    print("\n" + "="*70)
    print("  ✨ WORKFLOW COMPLETE".center(70))
    print("="*70)
    print(f"\n📊 Final Results:")
    print(f"   Approvals: {state.get('approvals', {})}")
    print()

if __name__ == "__main__":
    try:
        args = parse_args()
        run_conversational(stream=args.stream)
    except KeyboardInterrupt:
        print("\n\n🛑 Workflow interrupted (Ctrl+C)")
