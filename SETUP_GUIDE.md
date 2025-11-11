# Job Finding AI Agent - Local Setup Guide

This guide will help you run the complete application locally on your machine.

## Prerequisites

Before you start, make sure you have the following installed:

- **Java 21** (JDK 21)

  - Check: `java -version`
  - Download: https://adoptium.net/temurin/releases/

- **Maven 3.9+**

  - Check: `mvn -version`
  - Download: https://maven.apache.org/download.cgi

- **Node.js 16+** and npm

  - Check: `node -v` and `npm -v`
  - Download: https://nodejs.org/

- **Git**
  - Check: `git --version`

## Step 1: Clone the Repository

```bash
git clone <repository-url>
cd JobFindingAIAgent
```

## Step 2: Set Up Environment Variables

Create a `.env` file in the project root:

```bash
# .env
OPENAI_API_KEY=your-openai-api-key-here
JOBS_DB_URL=jdbc:sqlite:jobs.db
HEADLESS=true
```

**Important:**

- Get your OpenAI API key from: https://platform.openai.com/api-keys
- For now, we're using SQLite (local database). You can switch to Supabase PostgreSQL later.

## Step 3: Build the Backend

Navigate to the Java backend and build the project:

```bash
cd java/link-collector
mvn clean compile
```

This will:

- Download all dependencies
- Compile the Java code
- Run database migrations (creates `jobs.db`)

## Step 4: Start the Backend Agent API Server

You can launch the Java backend either from IntelliJ (recommended for quick iteration) or the command line.

### Option A — IntelliJ IDEA

1. Open `java/link-collector` as a module in IntelliJ.
2. Create a Run/Debug configuration:
   - Main class: `com.example.api.SimpleApiServer`
   - Working directory: project root or `java/link-collector`
   - VM/Program args: not required (set environment variables in the configuration if needed).
3. Run the configuration. IntelliJ will boot the agent API on the configured port (defaults to `http://localhost:7071`).

### Option B — Command Line

```bash
cd java/link-collector
mvn clean package
java -cp target/link-collector-0.1.0.jar com.example.api.SimpleApiServer
```

You should see:

```
✅ Agent API started on http://localhost:7071
```

**Keep IntelliJ (or this terminal) running!**

Test the backend:

```bash
curl http://localhost:8080/api/health
```

Expected response: `{"status":"ok","message":"Job Finding AI Agent API"}`

## Step 5: Install Frontend Dependencies

Open a **new terminal** and navigate to the frontend:

```bash
cd frontend
npm install
```

This will install all React dependencies (may take a few minutes).

## Step 6: Start the Frontend Development Server

From the `frontend` directory:

```bash
npm start
```

This will:

- Start the React development server
- Automatically open http://localhost:3000 in your browser

**Keep this terminal running!**

## Step 7: Start the Python Agent

Launch the Python agent from `AgentPlatform/` (still in this repo):

```bash
cd AgentPlatform
python -m venv .venv
source .venv/bin/activate        # or .venv\Scripts\activate on Windows
pip install -r requirements.txt
python -m app.interactive_demo --stream
```

> ℹ️ The `--stream` flag forwards progress updates to the Java agent API.  
> For development, it’s fine to run the Java backend inside IntelliJ and the Python agent in this repo. Future work can merge them into a shared process or container once the demo stabilizes.

## Step 8: Test the Application

You should now see the login page at http://localhost:3000

### Complete User Flow:

1. **Register a new account**

   - Click "Sign up"
   - Enter email, full name, and password
   - Click "Sign Up"

2. **Upload your resume**

   - Drag and drop a PDF resume
   - Wait for processing (extracts text and uses OpenAI to identify skills)
   - You'll be redirected to the dashboard

3. **View your dashboard**

   - See your identified skills displayed as green badges
   - Currently shows 0 job matches (we need to populate the database)

4. **Optional: Populate jobs database**
   - Stop the backend server (Ctrl+C)
   - Run the job scraper:
   ```bash
   mvn exec:java -Dexec.mainClass="com.example.app.Main" -Dexec.args="scrape-jobs 50"
   ```
   - Restart the backend server
   - Click "Refresh Matches" on the dashboard

## Architecture Overview

```
┌─────────────────────────────────────────────┐
│           Frontend (React)                  │
│         http://localhost:3000               │
│                                             │
│  - User Registration/Login                  │
│  - PDF Resume Upload                        │
│  - Job Matches Dashboard                    │
│  - AI-Powered Skills Display                │
└──────────────┬──────────────────────────────┘
               │ HTTP API Calls
               ▼
┌─────────────────────────────────────────────┐
│        Backend API (Java/Javalin)          │
│         http://localhost:8080/api           │
│                                             │
│  - REST API Endpoints                       │
│  - Resume Parsing (OpenAI)                  │
│  - Job Matching Logic                       │
│  - Cover Letter Generation                  │
└──────────────┬──────────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────────┐
│        Database (SQLite/PostgreSQL)         │
│                                             │
│  - users (profiles & skills)                │
│  - job_links (scraped URLs)                 │
│  - job_info (parsed job details)            │
│  - applications (generated cover letters)   │
└─────────────────────────────────────────────┘
```

## Common Issues & Solutions

### Issue: Port 3000 already in use

```bash
# Kill process on port 3000
lsof -ti:3000 | xargs kill -9
npm start
```

### Issue: Port 8080 already in use

```bash
# Kill process on port 8080
lsof -ti:8080 | xargs kill -9
# Restart backend
mvn exec:java -Dexec.mainClass="com.example.app.Main" -Dexec.args="api-server"
```

### Issue: "Failed to create account" or "Request failed with status code 404"

- Make sure the backend is running on port 8080
- Check backend terminal for error messages
- Verify `.env` file exists with `OPENAI_API_KEY`

### Issue: PDF upload fails

- Ensure your PDF contains actual text (not scanned images)
- Check that OpenAI API key is valid and has credits
- Look at backend terminal for error messages

### Issue: "No job matches yet"

- This is expected! The database is empty initially
- Run the job scraper to populate jobs (see Step 7 above)
- Upload your resume first before clicking "Refresh Matches"

## Project Structure

```
JobFindingAIAgent/
├── java/link-collector/          # Backend Java application
│   ├── src/main/java/
│   │   ├── com/example/
│   │   │   ├── api/              # REST API endpoints
│   │   │   ├── model/            # Data models
│   │   │   ├── persistence/      # Database repositories
│   │   │   ├── scrape/           # Job scraping & OpenAI
│   │   │   └── matcher/          # Job matching logic
│   │   └── resources/
│   │       └── db/migrations/    # Database schema
│   └── pom.xml                   # Maven dependencies
│
├── frontend/                      # React TypeScript frontend
│   ├── src/
│   │   ├── components/           # React components
│   │   │   ├── Login.tsx
│   │   │   ├── Register.tsx
│   │   │   ├── ResumeUpload.tsx
│   │   │   └── Dashboard.tsx
│   │   ├── services/
│   │   │   └── api.ts            # API client
│   │   └── App.tsx               # Main app & routing
│   └── package.json              # npm dependencies
│
├── .env                          # Environment variables
└── README.md                     # Project overview
```

## API Endpoints

The backend exposes these REST API endpoints:

| Method | Endpoint                             | Description                          |
| ------ | ------------------------------------ | ------------------------------------ |
| GET    | `/api/health`                        | Health check                         |
| POST   | `/api/users/register`                | Register new user                    |
| POST   | `/api/users/login`                   | Login user                           |
| GET    | `/api/users/{email}`                 | Get user profile                     |
| POST   | `/api/users/{email}/resume`          | Upload & parse resume                |
| GET    | `/api/users/{email}/matches`         | Get job matches                      |
| POST   | `/api/users/{email}/matches/refresh` | Refresh matches                      |
| POST   | `/api/users/{email}/applications`    | Apply to job (generate cover letter) |
| GET    | `/api/users/{email}/applications`    | Get user applications                |

Full API documentation: See `API_DOCUMENTATION.md`

## Development Tips

### Hot Reload

- **Frontend**: Changes auto-reload in browser
- **Backend**: Restart the `mvn exec:java` command after code changes

### Database Inspection

```bash
# SQLite
sqlite3 jobs.db
sqlite> .tables
sqlite> SELECT * FROM users;
sqlite> .quit
```

### Backend Logs

- All backend logs appear in the terminal where you ran `mvn exec:java`
- Look for errors related to OpenAI, database, or API requests

### Frontend Console

- Open browser DevTools (F12)
- Check Console tab for errors
- Check Network tab to see API requests

## Next Steps

After getting the app running locally:

1. **Test the complete flow** - Register, upload resume, view skills
2. **Scrape some jobs** - Run `scrape-jobs` command to populate database
3. **Test job matching** - Click "Refresh Matches" to see AI-matched jobs
4. **Generate applications** - Click "Apply with AI" to generate cover letters

## Getting Help

If you run into issues:

1. Check this guide's "Common Issues" section
2. Look at the terminal output for error messages
3. Check the browser console for frontend errors
4. Review the API documentation in `API_DOCUMENTATION.md`

## Production Deployment

For production deployment with Supabase PostgreSQL:

1. Update `.env` with Supabase connection string:
   ```
   JOBS_DB_URL=jdbc:postgresql://your-project.supabase.co:5432/postgres?user=...&password=...
   ```
2. Backend will automatically detect PostgreSQL and use appropriate migrations
3. Deploy backend as a standalone JAR or Docker container
4. Deploy frontend to Vercel, Netlify, or similar platform

See `SUPABASE_SETUP.md` for detailed Supabase configuration.

---

**Happy coding! 🚀**
