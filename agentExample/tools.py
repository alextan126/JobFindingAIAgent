from typing import List, Optional
from pydantic import BaseModel, Field
from langchain_core.tools import StructuredTool
import requests
import math
from similarityScore import tokenize, local_idf, tfidf_vector, cosine_sparse, top_overlap_terms


# ---------- 1) fetch_jobs tool ----------
class FetchJobsArgs(BaseModel):
    query: str = Field(..., description="Search keywords")
    locations: Optional[List[str]] = Field(default=None, description="Preferred locations")
    top_k: int = Field(20, description="Max number of jobs to return")

def fetch_jobs_func(query: str, locations: Optional[List[str]] = None, top_k: int = 20) -> List[dict]:
    """
    Finds a list of jobs from API, returning: [{'url': str, 'title': str, 'job_description': str, 'location': str}, ...]
    """
    # EXAMPLE: replace with your real API call
    # resp = requests.get("https://api.example/jobs", params={"q": query, "loc": locations, "k": top_k}, timeout=30)
    # data = resp.json()
    data = [
        {"url": "https://x/jobs/1", "title":"SWE Intern", "job_description":"We need Python + SQL...", "location": "SF"},
        {"url": "https://x/jobs/2", "title":"Platform Eng Intern", "job_description":"Kubernetes, Go, GCP...", "location": "Seattle"},
        {"url": "https://x/jobs/3", "title":"SWE Intern", "job_description":"We need C + SQL...", "location": "Seattle"},
        {"url": "https://x/jobs/4", "title":"SWE Intern", "job_description":"We need C++ + Java...", "location": "SF"},
    ]
    return data[:top_k]

fetch_jobs = StructuredTool.from_function(
    func=fetch_jobs_func,
    args_schema=FetchJobsArgs,
    description="Fetch job postings. Returns a list of objects with url, title, jd, location."
)

# ---------- 2) compare_jds tool ----------
# Can call outisde APIs to make a better comparison
class CompareArgs(BaseModel):
    user_jd: str = Field(..., description="User's reference JD/resume-like text to match against")
    job_jd: str = Field(..., description="Job description text to compare")

def compare_jds_func(user_jd: str, job_jd: str) -> dict:
   
    # --- minimal, pluggable placeholder ---
    """
    Compare two texts with TF-IDF (local IDF over the two docs) + cosine similarity.
    Returns a scalar score and top overlapping weighted terms (for debugging/explain).
    """
    u_tokens = tokenize(user_jd)
    j_tokens = tokenize(job_jd)

    # IDF from only these two documents
    idf = local_idf([u_tokens, j_tokens])

    u_vec = tfidf_vector(u_tokens, idf)
    j_vec = tfidf_vector(j_tokens, idf)

    score = cosine_sparse(u_vec, j_vec)
    terms = top_overlap_terms(u_vec, j_vec, k=15)

    return {
        "score_cosine": round(float(score), 4),
        "top_overlap_terms": [{"term": t, "weight": round(w, 4)} for t, w in terms],
        "notes": "Cosine similarity over TF-IDF with local (two-doc) IDF.",
    }

compare_jds = StructuredTool.from_function(
    func=compare_jds_func,
    args_schema=CompareArgs,
    description="Compare user's skills text vs a single job JD; returns a score and brief explanation."
)