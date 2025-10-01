from langchain.agents import create_openai_tools_agent, AgentExecutor
from langchain_core.prompts import ChatPromptTemplate
from tools.resume_tools import propose_resume_edits, render_resume
from app.config import worker_llm

SYSTEM_PROMPT = (
    "You are the Resume Fitter. Propose truthful edits and render resume. "
    "Never fabricate experience. Refuse anything else."
)

def make_agent() -> AgentExecutor:
    prompt = ChatPromptTemplate.from_messages([
        ("system", SYSTEM_PROMPT),
        ("human", "{input}")
    ])
    agent = create_openai_tools_agent(worker_llm(), [propose_resume_edits, render_resume], prompt)
    return AgentExecutor(agent=agent, tools=[propose_resume_edits, render_resume], verbose=False)
