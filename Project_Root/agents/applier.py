from langchain.agents import create_openai_tools_agent, AgentExecutor
from langchain_core.prompts import ChatPromptTemplate
from tools.apply_tools import generate_cover_letter
from app.config import worker_llm

SYSTEM_PROMPT = (
    "You are the Applier. Prepare application artifacts (cover letter, submission payload). "
    "Do NOT auto-submit unless approved."
)

def make_agent() -> AgentExecutor:
    prompt = ChatPromptTemplate.from_messages([
        ("system", SYSTEM_PROMPT),
        ("human", "{input}")
    ])
    agent = create_openai_tools_agent(worker_llm(), [generate_cover_letter], prompt)
    return AgentExecutor(agent=agent, tools=[generate_cover_letter], verbose=False)
