import os
from functools import lru_cache
from typing import Optional

from langchain_openai import ChatOpenAI

from app.backend_api import BackendAPI

BOSS_MODEL = "gpt-4o-mini"
WORKER_MODEL = "gpt-4o-mini"
HUMAN_APPROVAL_REQUIRED = True

# factory fns so tests can swap models

def boss_llm():
    return ChatOpenAI(model=BOSS_MODEL, temperature=0)

def worker_llm():
    return ChatOpenAI(model=WORKER_MODEL, temperature=0)


def _parse_bool(value: Optional[str], default: bool = False) -> bool:
    if value is None:
        return default
    return value.lower() in {"1", "true", "yes", "on"}


BACKEND_BASE_URL = os.getenv("BACKEND_BASE_URL")
BACKEND_API_KEY = os.getenv("BACKEND_API_KEY")
BACKEND_USE_STUB = _parse_bool(os.getenv("BACKEND_USE_STUB"), default=not bool(BACKEND_BASE_URL))

try:
    BACKEND_TIMEOUT = float(os.getenv("BACKEND_TIMEOUT", "30"))
except ValueError:
    BACKEND_TIMEOUT = 30.0


@lru_cache()
def backend_client() -> BackendAPI:
    """Return a singleton backend client configured from environment variables."""
    return BackendAPI(
        base_url=BACKEND_BASE_URL,
        api_key=BACKEND_API_KEY,
        use_stub=BACKEND_USE_STUB,
        request_timeout=BACKEND_TIMEOUT,
    )


__all__ = [
    "boss_llm",
    "worker_llm",
    "backend_client",
    "BOSS_MODEL",
    "WORKER_MODEL",
    "HUMAN_APPROVAL_REQUIRED",
    "BACKEND_BASE_URL",
    "BACKEND_API_KEY",
    "BACKEND_USE_STUB",
    "BACKEND_TIMEOUT",
]