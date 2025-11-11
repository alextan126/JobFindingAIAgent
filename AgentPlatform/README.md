# Job Application Agent Platform

An automated multi-agent workflow built with LangGraph that pulls jobs from a backend, tailors application materials, and streams progress back to the frontend.

## Features

- **JD Analyst**: Summarises each job and extracts structured requirements for later stages.
- **Resume Fitter**: Tailors the user’s resume (Markdown + PDF) based on the job summary.
- **Applier**: Generates a cover letter, renders it to PDF, and posts both PDFs plus the apply URL back to the backend.
- **Autonomous Supervisor**: Orchestrates JD → resume → apply for every job with zero manual input.
- **Backend Integration**: Single set of endpoints for jobs, resume bundle, progress updates, and final artifacts (stubbed by default).
- **Progress Streaming**: Optional `--stream` flag to mirror agent progress in the frontend progress feed.
- **CLI Tools**: Run the full flow headless or with console output for quick smoke-tests.

## Prerequisites

- Python 3.12+
- OpenAI API key
- Virtual environment support

## Installation

1. **Clone the repository:**

   ```bash
   git clone <repository-url>
   cd AgentPlatform
   ```

2. **Create and activate a virtual environment:**

   ```bash
   python3 -m venv venv
   source venv/bin/activate  # On Windows: venv\Scripts\activate
   ```

3. **Install dependencies:**

   ```bash
   pip install -r requirements.txt
   ```

4. **Set up your OpenAI API key:**

   ```bash
   export OPENAI_API_KEY="your-api-key-here"
   ```

   Or add it to your shell profile (`.bashrc`, `.zshrc`, etc.):

   ```bash
   echo 'export OPENAI_API_KEY="your-api-key-here"' >> ~/.zshrc
   source ~/.zshrc
   ```

## Usage

### Automated Demo

Run the automated demo. Pass `--stream` to forward progress updates to the frontend/backend (requires backend configuration, see below):

```bash
python -m app.interactive_demo           # local stub, console-only
python -m app.interactive_demo --stream  # stream events to configured backend
```

## How It Works

1. **Resume Bundle**: On startup the agent fetches the latest resume PDFs/text from the backend (or stub).
2. **Job Intake**: `GET /api/jobs` returns up to 30 jobs; the supervisor processes them sequentially.
3. **JD Analysis**: The JD agent extracts structure that downstream steps use.
4. **Resume Tailoring**: The resume fitter proposes edits, renders the updated Markdown, and converts it to a PDF.
5. **Application Packaging**: The applier drafts a cover letter, renders it to PDF, and calls `POST /api/results` with both PDFs plus the apply URL.
6. **Progress Feed**: Each stage emits `{ message, stage, timestamp }` via `POST /api/progress` when `--stream` (or `stream_progress`) is enabled.

## Project Structure

AgentPlatform/
├── app/
│   ├── backend_api.py        # Backend client (real endpoints or stub)
│   ├── config.py             # LLM + backend configuration
│   ├── graph.py              # LangGraph workflow definition
│   ├── interactive_demo.py   # CLI demo with optional progress streaming
│   ├── main.py               # Fire-and-forget sample runner
│   └── state.py              # Shared state schema
├── agents/                   # JD analyst, resume fitter, applier
├── common/pdf_utils.py       # Text → PDF helper
├── tools/                    # Reusable LangChain tools + progress bridge
├── JSONINFO.MD               # JSON payload reference for the integration
└── requirements.txt          # Python dependencies

## API Contract

The agreed JSON payloads for the frontend/backend bridge live in `JSONINFO.MD`. In short:

| Endpoint             | Method | Purpose                         |
|----------------------|--------|---------------------------------|
| `/api/jobs`          | GET    | Returns the batch of jobs.      |
| `/api/resume`        | GET    | Returns resume PDFs + plaintext.|
| `/api/results`       | POST   | Receives `{ jobId, resumePdfB64, coverLetterPdfB64, applyUrl }`. |
| `/api/progress`      | POST   | Receives `{ message, stage, timestamp }`. |

## Backend Configuration

Set the following environment variables to connect to your backend:

```bash
export BACKEND_BASE_URL="https://api.example.com"
export BACKEND_API_KEY="your-api-key"
export BACKEND_USE_STUB=false        # defaults to true when BACKEND_BASE_URL is unset
export BACKEND_TIMEOUT=30            # optional request timeout in seconds
```

- When `BACKEND_BASE_URL` is not provided (or `BACKEND_USE_STUB=true`) the workflow falls back to in-memory stubs for jobs, resume bundle, and results.
- Progress events are sent only when `stream_progress` is enabled (e.g., `python -m app.interactive_demo --stream`).
- If you expand the API surface, document the payloads in `JSONINFO.MD` to keep the frontend/agent/backends in sync.

## Configuration

Edit `app/config.py` to customise:
- `BOSS_MODEL`, `WORKER_MODEL`: Models used for supervisor/workers.
- `BACKEND_*`: Override base URL, API key, timeout, or force stub usage.
- `HUMAN_APPROVAL_REQUIRED`: Legacy flag (off by default in the automated flow).
