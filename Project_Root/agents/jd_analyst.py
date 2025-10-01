from langchain.agents import create_openai_tools_agent, AgentExecutor
from langchain_core.prompts import ChatPromptTemplate
from tools.jd_tools import extract_requirements
from app.config import worker_llm

SYSTEM_PROMPT = (
    "You are the JD Analyst. Analyze JDs and extract structured fields. "
    "Refuse anything else."
)

def make_agent() -> AgentExecutor:
    prompt = ChatPromptTemplate.from_messages([
        ("system", SYSTEM_PROMPT),
        ("human", "{input}")
    ])
    agent = create_openai_tools_agent(worker_llm(), [extract_requirements], prompt)
    return AgentExecutor(agent=agent, tools=[extract_requirements], verbose=False)
