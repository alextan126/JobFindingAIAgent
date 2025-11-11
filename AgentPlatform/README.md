# Job Application Agent Platform

An automated multi-agent workflow built with LangGraph that pulls jobs from a backend, tailors application materials, and streams progress back to the frontend.

## Features



## Prerequisites

- Python 3.12+
- OpenAI API key
- Virtual environment support

## Installation

### Option 1: Docker (Recommended)

The easiest way to run the Agent Platform is using Docker, which handles all dependencies automatically.

1. **Prerequisites:**
   - Docker and Docker Compose installed
   - OpenAI API key

2. **Set up your environment:**

   Create a `.env` file in the project root directory (one level up from `AgentPlatform/`):
   ```bash
   cd ..  # Go to project root
   echo "OPENAI_API_KEY=your-api-key-here" > .env
   ```

3. **Build and start the services:**
   ```bash
   docker-compose up -d
   ```

4. **Run the interactive demo:**
   ```bash
   docker-compose exec agent-platform python -m app.interactive_demo
   ```

5. **Development workflow:**
   - Edit code in `AgentPlatform/` directory - changes are reflected immediately (no rebuild needed)
   - Logs: `docker-compose logs agent-platform`
   - Stop services: `docker-compose down`

### Option 2: Local Installation

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

<pre>

## Project Structure

```
AgentPlatform/
├── app/
│   ├── config.py           # LLM configuration
│   ├── graph.py            # LangGraph workflow definition
│   ├── interactive_demo.py # Interactive demo script
│   ├── main.py             # Simple demo script
│   └── state.py            # State schema definition
├── agents/
│   ├── jd_analyst.py       # Job description analysis agent
│   ├── resume_fitter.py    # Resume tailoring agent
│   └── applier.py          # Application generation agent
├── adapters/
│   ├── toy_jobs.py         # Sample job data
│   └── toy_store.py        # Sample storage adapter
├── common/
│   └── models.py           # Pydantic models
├── tools/                  # Agent tools
├── requirements.txt        # Python dependencies
└── README.md               # Project documentation
```
</pre>

## Configuration
Edit app/config.py to customize:
Models: Change BOSS_MODEL and WORKER_MODEL
Temperature: Adjust model creativity (0 = deterministic)
Human Approval: Toggle HUMAN_APPROVAL_REQUIRED

Edit `app/config.py` to customise:
- `BOSS_MODEL`, `WORKER_MODEL`: Models used for supervisor/workers.
- `BACKEND_*`: Override base URL, API key, timeout, or force stub usage.
- `HUMAN_APPROVAL_REQUIRED`: Legacy flag (off by default in the automated flow).
