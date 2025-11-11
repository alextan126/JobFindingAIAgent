# Multi-Agent Job Application System

An AI-powered job application automation system that combines intelligent job scraping, matching, and personalized application generation.

## 🏗️ Architecture

This project consists of two main components:

1. **Java Backend** (`java/link-collector/`) - Job scraping, parsing, and database management
2. **Python Agent Platform** (`AgentPlatform/`) - AI-powered application workflow with LangGraph

## 🗄️ Database Support

The system supports both **SQLite** (local development) and **PostgreSQL/Supabase** (production):

- **SQLite**: Perfect for local development and testing
- **PostgreSQL (Supabase)**: Recommended for production deployments

### Quick Start with Supabase

See **[SUPABASE_SETUP.md](./SUPABASE_SETUP.md)** for detailed instructions.

**TL;DR:**
1. Create a Supabase project at [supabase.com](https://supabase.com)
2. Copy `.env.example` to `.env`
3. Update `JOBS_DB_URL` with your Supabase connection string
4. Run migrations: `java -jar link-collector.jar migrate`

### Environment Configuration

```bash
# Copy example environment file
cp .env.example .env

# Edit .env with your credentials
OPENAI_API_KEY=sk-your-key-here
JOBS_DB_URL=jdbc:postgresql://db.your-project.supabase.co:5432/postgres?user=postgres&password=your-password
HEADLESS=true
```

## 📚 Documentation

- **[API Documentation](./API_DOCUMENTATION.md)** - Complete REST API reference with examples
- [Supabase PostgreSQL Setup Guide](./SUPABASE_SETUP.md)
- [Quick Start Guide](./QUICKSTART.md)
- [Migration Summary](./MIGRATION_SUMMARY.md)
- [Java Backend README](./java/link-collector/README.md)
- [Python Agent Platform README](./AgentPlatform/README.md)

## 🚀 Quick Start

### Using Docker (Recommended)

```bash
# Set up environment variables
cp .env.example .env
# Edit .env with your credentials

# Start services
docker-compose up -d

# Run migrations
docker-compose exec link-collector java -jar target/link-collector-0.1.0.jar migrate

# Collect jobs
docker-compose exec link-collector java -jar target/link-collector-0.1.0.jar \
  collect-github https://github.com/SimplifyJobs/New-Grad-Positions/blob/main/README.md

# Scrape job details
docker-compose exec link-collector java -jar target/link-collector-0.1.0.jar scrape-jobs 10
```

### Using Local Installation

See component-specific READMEs for detailed setup instructions.

## 🤖 AI-Powered Features

- **Job Scraping**: OpenAI-powered extraction of structured data from job postings
- **Smart Matching**: Fuzzy skill matching with synonym recognition
- **Resume Tailoring**: Automatic resume customization for each job
- **Cover Letter Generation**: Personalized cover letters based on job requirements
- **Human-in-the-Loop**: Review and approve AI-generated content before applying

## 🔧 Technology Stack

**Backend:**
- Java 21, Maven, Playwright, PostgreSQL/SQLite, Flyway, OpenAI API

**Agent Platform:**
- Python 3.12+, LangGraph, LangChain, OpenAI API, Pydantic

**Infrastructure:**
- Docker, Docker Compose, Supabase (PostgreSQL)

---

## 📝 Development Notes

# AI usage: Write prompts with AIs by giving my description of what I want to workflow to do
# Before:

Determine the user's intent and respond with JSON:
{{
    "action": "approve|reject|redo_resume|redo_cover|show_resume|show_cover|quit|feedback",
    "feedback": "specific feedback to pass to agent (if action is feedback or redo)",
    "explanation": "brief explanation of what the user wants"
}}

Examples:
- "looks good" → {{"action": "approve", "feedback": "", "explanation": "User approves"}}
- "skip this job" → {{"action": "reject", "feedback": "", "explanation": "User wants to skip"}}
- "I haven't worked for ACME" → {{"action": "redo_resume", "feedback": "Remove experience section claiming work at ACME. Only include actual past experience.", "explanation": "User caught fabrication"}}
- "improve the resume" → {{"action": "redo_resume", "feedback": "Make improvements", "explanation": "User wants better resume"}}
- "show me resume" → {{"action": "show_resume", "feedback": "", "explanation": "User wants to see resume"}}
"""
# After:
 "RULES:
1. Auto-progress: JD → RESUME → APPLY (no HITL)
2. When all 3 done (jd_summary, rendered_resume, cover_letter) AND not yet approved → route to HITL
3. **APPROVAL SIGNALS**: If user says "looks good", "approve", "proceed", "continue", "yes", "apply", "submit" → SET approve_job=true AND route to APPLY
4. **REJECTION SIGNALS**: If user says "skip", "reject", "no", "next job" → SET approve_job=false
5. **REFINEMENT**: If user mentions problems or wants changes → extract feedback, clear relevant artifacts, route to fix
6. If user asks to "show" something → route to HITL (it will display)
