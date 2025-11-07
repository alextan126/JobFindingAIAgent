# Job Application Agent Platform

A conversational multi-agent system that automates job application processes using LangGraph. The system includes intelligent agents for job description analysis, resume tailoring, and application generation, with human-in-the-loop supervision.

## Features

- **JD Analyst**: Analyzes job descriptions and extracts structured requirements
- **Resume Fitter**: Tailors resumes to match specific job requirements
- **Applier**: Generates personalized cover letters
- **Intelligent Supervisor**: Manages workflow and makes routing decisions
- **Human-in-the-Loop**: Interactive approval and feedback system
- **Interactive-Tool*: Coomand line tool to interactively act with agent for test/dev/demo

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

## Interactive Demo

Run the conversational demo where you can interact with the agent with toy examples, see Job Description in adapters:

```bash
python -m app.interactive_demo
```

## How It Works

1. **Job Loading**: The system loads 3 sample jobs from the toy data 
2. **JD Analysis**: Analyzes each job description and extracts key requirements
3. **Resume Tailoring**: Customizes your resume to match job requirements
4. **Human Review**: Supervisor pauses for your approval and feedback
5. **Cover Letter Generation**: Creates personalized cover letters
6. **Queue Processing**: Automatically moves to the next job

## Interactive Commands

When the supervisor asks for input, you can use natural language:

- **Approval**: "looks good", "proceed", "approve", "continue"
- **Rejection**: "skip this job", "reject", "next job"
- **Refinement**: "improve the resume", "rewrite the cover letter"
- **Display**: "show me the resume", "show the cover letter"
- **Exit**: "quit", "exit", "stop"

## Project Structure
AgentPlatform/
├── app/
│   ├── config.py          # LLM configuration
│   ├── graph.py           # LangGraph workflow definition
│   ├── interactive_demo.py # Interactive demo script
│   ├── main.py            # Simple demo script
│   └── state.py           # State schema definition
├── agents/
│   ├── jd_analyst.py      # Job description analysis agent
│   ├── resume_fitter.py   # Resume tailoring agent
│   └── applier.py         # Application generation agent
├── adapters/
│   ├── toy_jobs.py        # Sample job data
│   └── toy_store.py       # Sample storage adapter
├── common/
│   └── models.py          # Pydantic models
├── tools/                 # Agent tools
├── requirements.txt       # Python dependencies
└── README.md             # This file

##Configuration
Edit app/config.py to customize:
Models: Change BOSS_MODEL and WORKER_MODEL
Temperature: Adjust model creativity (0 = deterministic)
Human Approval: Toggle HUMAN_APPROVAL_REQUIRED

