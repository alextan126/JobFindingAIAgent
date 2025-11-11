# Integration Payload Reference

All services run locally (frontend ≈ `http://localhost:3000`, agent service ≈ `http://localhost:7070`). Authentication is not required for the demo.

## Python Agent Service (Frontend-facing)

### `GET /api/jobs`

Fetch the cached job list. Jobs are bootstrapped from the Java service at startup and can be augmented via `POST /api/nextJob`.

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

### `POST /api/uploadResume`

Upload the user-provided resume bundle for the current session. PDFs are stored in memory until `POST /api/reset`.

```json
{
  "resumePdfB64": "<base64-encoded resume PDF>",
  "projectsPdfB64": "<base64-encoded projects PDF>",
  "resumeText": "Plaintext resume extracted client-side (optional)"
}
```

### `GET /api/resume`

Return the current resume bundle stored in memory.

```json
{
  "resume": {
    "resumePdfB64": "<base64-encoded resume PDF>",
    "projectsPdfB64": "<base64-encoded projects PDF>",
    "resumeText": "Plaintext resume extracted client-side (optional)"
  }
}
```

### `POST /api/nextJob`

Advance the agent to the next queued job and kick off processing. No request body is required; the service already has the batch cached from the initial backend fetch (`{}` is fine). Response is empty (`204 No Content`) if a run starts successfully, or `409` if the previous job is still in progress. Optionally pass `{"jobId": "..."}` to force a specific job that is already in the queue.

### `GET /api/progress`

Retrieve progress events emitted during the current/last LangGraph run. Returns most recent events first.

```json
{
  "events": [
    {
      "message": "Analyzing JD for Acme Robotics",
      "stage": "jd_analysis",
      "timestamp": "2025-11-11T03:21:45.123Z",
      "jobId": "JOB-123"
    }
  ]
}
```

### `GET /api/results`

Return the latest application package the agent produced.

```json
{
  "results": [
    {
      "jobId": "JOB-123",
      "resumePdfB64": "<base64-encoded tailored resume PDF>",
      "coverLetterPdfB64": "<base64-encoded tailored cover letter PDF>",
      "applyUrl": "https://company.jobs/apply/123",
      "createdAt": "2025-11-11T03:22:05.512Z"
    }
  ]
}
```

### `POST /api/reset`

Clear cached resumes, jobs, progress, and results so the next session can start cleanly.

```json
{
  "cleared": true
}
```

## Java Bootstrap Service (optional)

The legacy Java API remains available for seeding data when needed:

- `GET /api/jobs` — returns an array of jobs (`jobs: [...]`).
- `GET /api/resume` — returns the resume bundle (`resumePdfB64`, etc.).
- `POST /api/results` — historical endpoint for tailored packages (unused once the Python service handles frontend traffic).
- `POST /api/progress` — historical endpoint for progress events.

The Python service calls these endpoints once during startup (or on demand) to populate its in-memory caches when `BACKEND_USE_STUB=false`.
