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
