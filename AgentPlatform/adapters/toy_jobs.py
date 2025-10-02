FAKE_JOBS = [
    {
        "id": "J-001",
        "company": "Acme AI",
        "jd": """Title: Software Engineer (Backend)\nLocation: Remote (US)\nVisa: Yes, H1B/OPT considered\nMust-have: Python, Postgres, REST, 2+ yrs\nNice-to-have: LangChain, AWS\nSalary: 140k-170k\nDescription: Work on APIs and data pipelines..."""
    },
    {
        "id": "J-002",
        "company": "Nimbus Cloud",
        "jd": """Title: Data Engineer\nLocation: San Francisco, CA\nVisa: No\nMust-have: SQL, Airflow, Python, 3+ yrs\nNice-to-have: Spark, Kafka\nSalary: 150k-180k\nDescription: Build reliable batch/stream ETL..."""
    },
    {
        "id": "J-003",
        "company": "Vector Labs",
        "jd": """Title: ML Engineer\nLocation: Seattle, WA (Hybrid)\nVisa: Case-by-case\nMust-have: Python, ML lifecycle, model serving\nNice-to-have: LangGraph, Triton, Kubernetes\nSalary: 160k-190k\nDescription: Productionize models..."""
    }
]

def toy_fetch_jobs(limit=3):
    return FAKE_JOBS[:limit]