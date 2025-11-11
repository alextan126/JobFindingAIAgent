# Job Link Collector

This is the part of our project that collects and classifies job posting links from sites like Lever, Greenhouse, AshbyHQ, MyWorkday, and Other.  
It stores them in a local SQLite database (`jobs.db`) using Flyway for migrations.

The idea is to build a small data ingestion layer that can later power and serve as tools for our automated job tracking and filtering AI Agents.

---

## How it works

- The `migrate` command runs Flyway migrations to create and update the SQLite schema.
- The `collect-github` command scrapes links from a GitHub page (like the SimplifyJobs repo).
- Each link is classified by type (Lever, Greenhouse, AshbyHQ, etc.) and stored in the `job_links` table.
- Duplicates are ignored, timestamps are added automatically.
- The `job_posts` table is planned for storing full scraped job descriptions later.

---

## Tables

### job_links
- id (INTEGER, primary key)
- url (TEXT, unique)
- host_type (TEXT)
- source (TEXT)
- discovered_at (TEXT)
- status (TEXT: new, checked, error)
- last_checked_at (TEXT)
- last_error (TEXT)

### job_posts
- id (INTEGER, primary key)
- url (TEXT, unique)
- platform (TEXT)
- title (TEXT)
- company (TEXT)
- location (TEXT)
- apply_url (TEXT)
- description_text (TEXT)
- scraped_at (TEXT)
- http_status (INTEGER)

---

## AI usage

I used ChatGPT for parts of this project.  
All code was reviewed and edited manually. AI mainly helped with syntax, refactoring, and writing SQL queries.

### Example prompts and notes

**Prompt 1:** “Write a Java function that collects all hyperlinks from a page using Playwright.”  
Helpful for structure, but I fixed the selectors and added exception handling myself.

**Prompt 2:** “How to use Flyway with SQLite.”  
AI showed migration examples, I replaced unsupported PostgreSQL types.

**Prompt 3:** “Optimize a join between job_links and job_posts.”  
Suggested a LEFT JOIN and indexes. I rewrote for SQLite syntax and pagination.

Sources used:
- SQLite docs
- Flyway docs
- Playwright for Java docs
- Stack Overflow examples
- ChatGPT

---

## Running it
```bash
# Build
mvn clean package

# Run migrations
java -jar target/link-collector-0.1.0.jar migrate

# Collect links from GitHub
HEADLESS=false java -jar target/link-collector-0.1.0.jar collect-github "https://github.com/SimplifyJobs/New-Grad-Positions/blob/dev/README.md"

# Inspect database
sqlite3 jobs.db
```

---

## Agent API (for the AI workflow)

A lightweight HTTP server exposes the data the AI agent expects.  
It runs beside the ingestion pipeline and reads directly from the same SQLite database.

### Environment variables

| Variable             | Purpose                                                | Default                  |
|----------------------|--------------------------------------------------------|--------------------------|
| `JDBC_URL`           | JDBC connection string                                 | `jdbc:sqlite:jobs.db`    |
| `AGENT_API_PORT`     | Port to bind                                           | `7071`                   |
| `AGENT_JOB_LIMIT`    | Maximum jobs to return in `/api/jobs`                  | `30`                     |
| `AGENT_RESUME_EMAIL` | Optional user email to source resume text/PDF          | _none_ (returns empty)   |
| `AGENT_PROJECTS_PDF` | Optional path to a supporting projects PDF             | _none_ (returns empty)   |

### Start the server

```bash
# After mvn clean package
java -cp target/link-collector-0.1.0.jar com.example.api.SimpleApiServer
```

### Quick smoke-tests

```bash
# Job payload (matches JSONINFO.MD)
curl http://localhost:7071/api/jobs | jq

# Resume bundle (returns base64 fields + plaintext)
curl http://localhost:7071/api/resume | jq

# Push a progress update
curl -X POST http://localhost:7071/api/progress \
  -H 'Content-Type: application/json' \
  -d '{"message":"Tailoring resume","stage":"resume_tailoring","timestamp":"2025-11-11T08:00:00Z"}'

# Push results (tailored PDFs)
curl -X POST http://localhost:7071/api/results \
  -H 'Content-Type: application/json' \
  -d '{"jobId":"JOB-123","resumePdfB64":"...","coverLetterPdfB64":"...","applyUrl":"https://company/jobs/123"}'

# Inspect buffered results/progress
curl http://localhost:7071/api/results | jq
curl http://localhost:7071/api/progress | jq
```
