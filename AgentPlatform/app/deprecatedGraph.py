from langgraph.graph import StateGraph, START, END
from langgraph.checkpoint.memory import MemorySaver
from langchain_core.messages import HumanMessage, AIMessage
from pydantic import BaseModel, Field
from typing import Optional, List

from app.state import AppState
from app.config import boss_llm

# Import custom agents
from agents.jd_analyst import jd_analyst_agent
from agents.resume_fitter import resume_fitter_agent
from agents.applier import applier_agent

# -----------------------
# Supervisor Decision Model
# -----------------------
class SupervisorDecision(BaseModel):
    """Supervisor's routing decision"""
    route: str = Field(description="Next route: JD, RESUME, APPLY, HITL, or DONE")
    reasoning: str = Field(description="Brief explanation of the decision")
    user_feedback: Optional[str] = Field(default="", description="Specific feedback to pass to agents")
    approve_job: Optional[bool] = Field(default=None, description="True to approve, False to reject, None if not deciding")
    clear_artifacts: List[str] = Field(default_factory=list, description="Artifacts to regenerate")

# -----------------------
# Node Functions
# -----------------------

def jd_analyst_node(state: AppState) -> AppState:
    job = state.get("current_job")
    if not job:
        return {**state, "route": "DONE"}
    
    print(f"\n{'='*60}")
    print(f"📊 JD ANALYST - {job['company']}")
    print(f"{'='*60}")
    
    result = jd_analyst_agent(state)
    print(f"\n⬅️  Returning to SUPERVISOR")
    
    return {**result, "route": None}

def resume_fitter_node(state: AppState) -> AppState:
    print(f"\n{'='*60}")
    print(f"📝 RESUME FITTER")
    print(f"{'='*60}")
    
    user_feedback = state.get("user_feedback", "")
    if user_feedback:
        print(f"💬 Incorporating feedback: {user_feedback}")
    
    result = resume_fitter_agent(state)
    print(f"\n⬅️  Returning to SUPERVISOR")
    
    result["user_feedback"] = ""
    return {**result, "route": None}

def applier_node(state: AppState) -> AppState:
    print(f"\n{'='*60}")
    print(f"📧 APPLIER")
    print(f"{'='*60}")
    
    user_feedback = state.get("user_feedback", "")
    if user_feedback:
        print(f"💬 Incorporating feedback: {user_feedback}")
    
    result = applier_agent(state)
    print(f"\n⬅️  Returning to SUPERVISOR")
    
    result["user_feedback"] = ""
    return {**result, "route": None}

def supervisor_node(state: AppState) -> AppState:
    """Intelligent supervisor"""
    print(f"\n🔄 SUPERVISOR - Evaluating Situation")
    
    # Initial setup
    if not state.get("current_job") and not state.get("queue"):
        from adapters.toy_jobs import toy_fetch_jobs
        jobs = toy_fetch_jobs(limit=3)
        if not jobs:
            return {**state, "route": "DONE"}
        first, rest = jobs[0], jobs[1:]
        
        print(f"📥 Loaded {len(jobs)} jobs. Starting with {first['company']}")
        print(f"➡️  Routing to: JD_ANALYST")
        
        return {
            **state,
            "current_job": first,
            "queue": rest,
            "route": "JD"
        }
    
    # Get context
    artifacts = state.get("artifacts", {})
    job = state.get("current_job", {})
    job_id = job.get("id")
    approvals = state.get("approvals", {})
    messages = state.get("messages", [])
    queue = state.get("queue", [])
    
    # Check if job was just rejected
    if job_id in approvals and not approvals[job_id]:
        if queue:
            next_job = queue.pop(0)
            print(f"❌ Job {job_id} rejected. Moving to: {next_job['company']}")
            return {
                **state,
                "artifacts": {},
                "queue": queue,
                "current_job": next_job,
                "messages": [],
                "route": "JD"
            }
        else:
            print(f"❌ Job {job_id} rejected. No more jobs.")
            return {**state, "route": "DONE"}
    
    # Check if job complete
    if job_id in approvals and approvals[job_id] and "cover_letter" in artifacts:
        if queue:
            next_job = queue.pop(0)
            print(f"✅ Job {job_id} complete! Moving to: {next_job['company']}")
            return {
                **state,
                "artifacts": {},
                "queue": queue,
                "current_job": next_job,
                "messages": [],
                "route": "JD"
            }
        else:
            print(f"🎉 All {len(approvals)} jobs processed!")
            return {**state, "route": "DONE"}
    
    # Check for user input
    has_user_input = messages and isinstance(messages[-1], HumanMessage)
    user_message = messages[-1].content if has_user_input else None
    
    # Let LLM decide with EXPLICIT approval instructions
    decision_prompt = f\"\"\"You are a supervisor managing job applications.

STATUS:
- Job: {job.get('company')} ({job_id})
- Completed: {list(artifacts.keys())}
- Approved: {approvals.get(job_id, 'NO - not yet approved')}
- Queue: {len(queue)} more jobs

USER SAID: \"{user_message if user_message else 'nothing'}\"

RULES:
1. Auto-progress: JD → RESUME → APPLY (no HITL)
2. When all 3 done (jd_summary, rendered_resume, cover_letter) AND not yet approved → route to HITL
3. **APPROVAL SIGNALS**: If user says \"looks good\", \"approve\", \"proceed\", \"continue\", \"yes\", \"apply\", \"submit\" → SET approve_job=true AND route to APPLY
4. **REJECTION SIGNALS**: If user says \"skip\", \"reject\", \"no\", \"next job\" → SET approve_job=false AND route to JD (next job)
5. **REFINEMENT**: If user mentions problems or wants changes → extract feedback, clear relevant artifacts, route to fix
6. If user asks to \"show\" something → route to HITL (it will display)

CRITICAL: 
- When you detect approval (step 3), you MUST set approve_job=true AND route to APPLY to generate cover letter!
- When you detect rejection (step 4), you MUST set approve_job=false AND route to JD to move to next job!

Decide now:
\"\"\"

    response = boss_llm().with_structured_output(SupervisorDecision).invoke(decision_prompt)
    
    print(f\"🤖 Decision: {response.reasoning}\")
    print(f\"➡️  Routing to: {response.route}\")
    
    # Update state
    new_state = {**state, \"route\": response.route, \"messages\": []}
    
    if response.user_feedback:
        new_state[\"user_feedback\"] = response.user_feedback
        print(f\"💬 Feedback to agent: {response.user_feedback[:100]}...\")
    
    if response.clear_artifacts:
        artifacts_copy = {**artifacts}
        for key in response.clear_artifacts:
            artifacts_copy.pop(key, None)
        new_state[\"artifacts\"] = artifacts_copy
        print(f\"🗑️  Clearing: {response.clear_artifacts}\")
    
    if response.approve_job is not None:
        new_state[\"approvals\"] = {**approvals, job_id: response.approve_job}
        print(f\"{'✅' if response.approve_job else '❌'} Job {job_id}: {'APPROVED' if response.approve_job else 'REJECTED'}\")
    
    return new_state

def hitl_node(state: AppState) -> AppState:
    \"\"\"Human-in-the-loop for displaying info and getting input\"\"\"
    job = state.get(\"current_job\", {})
    artifacts = state.get(\"artifacts\", {})
    messages = state.get(\"messages\", [])
    
    print(f\"\\n{'='*60}\")
    print(f\"👤 HUMAN CONSULTATION - {job.get('company')}\")
    print(f\"{'='*60}\")
    
    # Display info if requested  
    if messages and isinstance(messages[-1], HumanMessage):
        user_input = messages[-1].content.lower()
        
        if 'resume' in user_input and any(w in user_input for w in ['show', 'see', 'display']):
            print(f\"\\n📄 CURRENT RESUME:\")
            print(\"=\"*60)
            print(artifacts.get('rendered_resume', 'No resume yet'))
            print(\"=\"*60)
            return {**state, \"messages\": [], \"route\": \"HITL\"}
        
        elif 'cover' in user_input and any(w in user_input for w in ['show', 'see', 'display']):
            print(f\"\\n📨 COVER LETTER:\")
            print(\"=\"*60)
            print(artifacts.get('cover_letter', 'No cover letter yet'))
            print(\"=\"*60)
            return {**state, \"messages\": [], \"route\": \"HITL\"}
    
    print(f\"⏸️  Waiting for your input...\")
    return {**state, \"route\": \"HITL\"}

# -----------------------
# Build Graph
# -----------------------
graph = StateGraph(AppState)

graph.add_node(\"supervisor\", supervisor_node)
graph.add_node(\"jd_analyst\", jd_analyst_node)
graph.add_node(\"resume_fitter\", resume_fitter_node)
graph.add_node(\"applier\", applier_node)
graph.add_node(\"hitl\", hitl_node)

graph.add_edge(START, \"supervisor\")
graph.add_conditional_edges(
    \"supervisor\",
    lambda s: s.get(\"route\"),
    {
        \"JD\": \"jd_analyst\",
        \"RESUME\": \"resume_fitter\",
        \"APPLY\": \"applier\",
        \"HITL\": \"hitl\",
        \"DONE\": END
    }
)

for node in [\"jd_analyst\", \"resume_fitter\", \"applier\", \"hitl\"]:
    graph.add_edge(node, \"supervisor\")

deprecated_app = graph.compile(checkpointer=MemorySaver())

