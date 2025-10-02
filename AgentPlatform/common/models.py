from pydantic import BaseModel, Field
from typing import List, Optional

class JDSummary(BaseModel):
    job_id: str
    company: Optional[str] = None
    title: Optional[str] = None
    location: Optional[str] = None
    salary: Optional[str] = None
    visa_sponsorship: Optional[str] = None
    must_have: List[str] = Field(default_factory=list)
    nice_to_have: List[str] = Field(default_factory=list)
    tech_stack: List[str] = Field(default_factory=list)