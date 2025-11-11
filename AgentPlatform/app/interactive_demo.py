from app.graph import app
from app.state import AppState
from langchain_core.messages import HumanMessage


def run_conversational():
    """Run workflow with conversational HITL via the command line."""
    print("\n" + "=" * 70)
    print("  🚀 CONVERSATIONAL JOB APPLICATION AGENT".center(70))
    print("=" * 70)
    print("\nThe supervisor will consult you at key decision points.")
    print("You can give feedback in natural language!\n")

    state: AppState = {
        "user_goal": "Apply to backend jobs. Respect visa policy.",
        "current_job": None,
        "queue": [],
        "artifacts": {},
        "approvals": {},
        "messages": [],
        "route": None,
        "stream_progress": False,
        "resume_text": None,
        "resume_pdf_b64": None,
    }

    config = {"configurable": {"thread_id": "conv-demo"}}

    while True:
        for event in app.stream(state, config=config, stream_mode="values"):
            state = event

            if state.get("route") == "HITL":
                messages = state.get("messages", [])
                last_message = messages[-1] if messages else None

                if not last_message or not isinstance(last_message, HumanMessage):
                    print("\n" + "─" * 70)
                    supervisor_prompt = (
                        last_message.content if last_message else "How should we proceed?"
                    )
                    print("💬 SUPERVISOR:", supervisor_prompt)
                    print("─" * 70)
                    print("\nYou can say things like:")
                    print("  • looks good, proceed")
                    print("  • skip this job")
                    print("  • improve the resume")
                    print("  • rewrite the cover letter")
                    print("  • quit")

                    user_input = input("\n👤 You: ").strip()

                    if user_input.lower() in {"quit", "exit", "stop"}:
                        print("\n🛑 Stopping workflow...")
                        return

                    state.setdefault("messages", []).append(HumanMessage(content=user_input))
                    continue

            if state.get("route") == "DONE":
                break

        if state.get("route") == "DONE":
            break

    print("\n" + "=" * 70)
    print("  ✨ WORKFLOW COMPLETE".center(70))
    print("=" * 70)
    print(f"\n📊 Final Results:")
    print(f"   Approvals: {state.get('approvals', {})}")
    print()


if __name__ == "__main__":
    try:
        run_conversational()
    except KeyboardInterrupt:
        print("\n\n🛑 Workflow interrupted (Ctrl+C)")
