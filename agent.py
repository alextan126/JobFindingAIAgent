"""Minimal LangChain agent skeleton using OpenAI via LangChain.

Set the OPENAI_API_KEY environment variable before running this script.
"""

import os
from typing import Tuple

from dotenv import load_dotenv
from langchain_openai import ChatOpenAI
from langgraph.prebuilt import create_react_agent


load_dotenv()


def build_model() -> ChatOpenAI:
    api_key = os.environ.get("OPENAI_API_KEY")
    if not api_key:
        raise RuntimeError("Missing OPENAI_API_KEY environment variable.")
    # ChatOpenAI reads OPENAI_API_KEY from the environment.
    return ChatOpenAI(model="gpt-4o-mini", temperature=0)


def build_agent() -> Tuple[object, ChatOpenAI]:
    model = build_model()
    tools = []  # Placeholder: add tools as you implement them
    agent = create_react_agent(model, tools)
    return agent, model


if __name__ == "__main__":
    agent, _ = build_agent()
    print("Agent initialized. Ready to add tools.")
