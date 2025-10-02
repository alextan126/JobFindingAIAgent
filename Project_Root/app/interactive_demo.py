from app.graph import app
from app.state import AppState
from langchain_core.messages import HumanMessage

def run_conversational():
    """Run workflow with conversational HITL"""
    print("\n" + "="*70)
    print("  🚀 CONVERSATIONAL JOB APPLICATION AGENT".center(70))
    print("="*70)
    print("\nThe supervisor will consult you at key decision points.")
    print("You can give feedback in natural language!\n")
    
    state: AppState = {
        "user_goal": "Apply to backend jobs. Respect visa policy.",
        "resume_md": "# Alex Tan — Resume\n\n- Python, SQL, Airflow, LangChain, AWS\n- Projects: WeatherApp (FastAPI, Postgres)\n- Education: MSCS @ USF\n",
        "current_job": None,
        "queue": [],
        "artifacts": {},
        "approvals": {},
        "messages": [],
        "route": None
    }
    
    config = {"configurable": {"thread_id": "conv-demo"}}
    
    while True:
        # Stream events
        for event in app.stream(state, config=config, stream_mode="values"):
            state = event
            
            # If in HITL and waiting for input
            if state.get('route') == 'HITL':
                # Check if we already have user message
                messages = state.get('messages', [])
                if not messages or not isinstance(messages[-1], HumanMessage):
                    # Need user input
                    print("\n" + "─"*70)
                    print("💬 SUPERVISOR:", messages[-1].content if messages else "What would you like to do?")
                    print("─"*70)
                    print("\nYou can say things like:")
                    print("  • 'looks good, proceed'")
                    print("  • 'skip this job'")
                    print("  • 'improve the resume'")
                    print("  • 'rewrite the cover letter'")
                    print("  • 'quit'")
                    
                    user_input = input("\n👤 You: ").strip()
                    
                    if user_input.lower() in ['quit', 'exit', 'stop']:
                        print("\n🛑 Stopping workflow...")
                        return
                    
                    # Add user message and continue
                    state['messages'].append(HumanMessage(content=user_input))
                    continue
            
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
        run_conversational()
    except KeyboardInterrupt:
        print("\n\n🛑 Workflow interrupted (Ctrl+C)")
