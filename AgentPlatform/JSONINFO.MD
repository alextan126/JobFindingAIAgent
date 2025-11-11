# Integration Payload Reference

All services run locally (frontend ≈ `localhost:3000`, agent ≈ `localhost:<agent_port>`, backend ≈ `localhost:<backend_port>`). Authentication is not required for the demo.

## 1. Job Fetch Response

Endpoint: `GET /api/jobs` (stub; returns 30 jobs for the current run)

```json
{
  "jobs": [
    {
      "jobId": "JOB-123",
      "company": "Acme Robotics",
      "title": "Software Engineer",
      "location": "San Francisco, CA",
      "description": "Full job description text...",
      "applyUrl": "https://company.jobs/apply/123"
    }
  ]
}
```

## 2. Resume Source

Endpoint: `GET /api/resume`

```json
{
  "resumePdfB64": "<base64-encoded resume PDF>",
  "projectsPdfB64": "<base64-encoded related projects PDF>",
  "resumeText": "Plaintext version of the resume for LLM prompts"
}
```

## 3. Tailored Output Upload

Endpoint: `POST /api/results`

```json
{
  "jobId": "JOB-123",
  "resumePdfB64": "<base64-encoded tailored resume PDF>",
  "coverLetterPdfB64": "<base64-encoded tailored cover letter PDF>",
  "applyUrl": "https://company.jobs/apply/123"
}
```

This endpoint does not persist artifacts; it forwards them to the frontend session for download.

## 4. Progress Updates

Endpoint: `POST /api/progress`

```json
{
  "message": "Tailoring resume for Acme Robotics",
  "stage": "resume_tailoring",
  "timestamp": "2025-11-11T03:21:45.123Z"
}
```

The agent emits updates when major LangGraph nodes start/finish (e.g., `job_loaded`, `jd_analysis`, `resume_tailoring`, `application_package`).
