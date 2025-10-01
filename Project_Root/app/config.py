from langchain_openai import ChatOpenAI

BOSS_MODEL = "gpt-4o-mini"
WORKER_MODEL = "gpt-4o-mini"
HUMAN_APPROVAL_REQUIRED = True

# factory fns so tests can swap models

def boss_llm():
    return ChatOpenAI(model=BOSS_MODEL, temperature=0)

def worker_llm():
    return ChatOpenAI(model=WORKER_MODEL, temperature=0)