# Job Finding AI Agent - REST API Documentation

Base URL: `http://localhost:8080/api`

All endpoints return JSON. CORS is enabled for all origins.

## Table of Contents
- [Health Check](#health-check)
- [User Endpoints](#user-endpoints)
- [Job Endpoints](#job-endpoints)
- [Job Matching Endpoints](#job-matching-endpoints)
- [Application Endpoints](#application-endpoints)

---

## Health Check

### GET /health
Check if the API server is running.

**Response:**
```json
{
  "status": "ok",
  "message": "Job Finding AI Agent API"
}
```

---

## User Endpoints

### POST /users/register
Create a new user account.

**Request Body:**
```json
{
  "fullName": "John Doe",
  "email": "john@example.com",
  "password": "securepassword123"
}
```

**Response (201 Created):**
```json
{
  "id": 1,
  "fullName": "John Doe",
  "email": "john@example.com",
  "createdAt": "2025-11-10T18:00:00Z"
}
```

**Error Responses:**
- `400 Bad Request` - Missing required fields
- `409 Conflict` - User already exists

---

### POST /users/login
Authenticate a user.

**Request Body:**
```json
{
  "email": "john@example.com",
  "password": "securepassword123"
}
```

**Response (200 OK):**
```json
{
  "id": 1,
  "fullName": "John Doe",
  "email": "john@example.com",
  "skills": "[\"Python\", \"React\", \"Docker\"]",
  "experienceLevel": "mid"
}
```

**Error Responses:**
- `400 Bad Request` - Missing email or password
- `401 Unauthorized` - Invalid credentials

---

### GET /users/{email}
Get user profile information.

**Path Parameters:**
- `email` - User's email address

**Response (200 OK):**
```json
{
  "id": 1,
  "fullName": "John Doe",
  "email": "john@example.com",
  "skills": "[\"Python\", \"React\", \"Docker\"]",
  "experienceLevel": "mid",
  "experienceYears": "3",
  "education": "Bachelor's in Computer Science"
}
```

**Error Responses:**
- `404 Not Found` - User not found

---

### POST /users/{email}/resume
Parse a resume and extract skills using AI.

**Path Parameters:**
- `email` - User's email address

**Request Body:**
```json
{
  "resumeText": "John Doe\nSoftware Engineer\n\nSkills: Python, React, Docker, AWS...\n\nExperience:\n- Senior Developer at TechCorp (3 years)..."
}
```

**Response (200 OK):**
```json
{
  "message": "Resume parsed successfully",
  "skills": "[\"Python\", \"React\", \"Docker\", \"AWS\", \"PostgreSQL\"]",
  "experienceLevel": "mid",
  "experienceYears": "3"
}
```

**Error Responses:**
- `400 Bad Request` - Missing resumeText
- `404 Not Found` - User not found

---

## Job Endpoints

### GET /jobs
Get all jobs in the database.

**Response (200 OK):**
```json
{
  "total": 150,
  "jobs": [
    {
      "id": 1,
      "jobLinkId": 42,
      "title": "Senior Backend Engineer",
      "company": "TechCorp",
      "location": "San Francisco, CA",
      "remoteType": "hybrid",
      "salary": "$150k-$200k",
      "description": "We are looking for...",
      "requirements": "[\"Python\", \"Django\", \"PostgreSQL\", \"AWS\"]",
      "jobType": "Full-time",
      "postedDate": "2025-11-01",
      "applicationUrl": "https://techcorp.com/careers/apply/123",
      "scrapedAt": "2025-11-10T18:00:00Z",
      "scrapeSuccess": true
    }
  ]
}
```

---

### GET /jobs/{id}
Get a specific job by ID.

**Path Parameters:**
- `id` - Job ID (integer)

**Response (200 OK):**
```json
{
  "id": 1,
  "jobLinkId": 42,
  "title": "Senior Backend Engineer",
  "company": "TechCorp",
  "location": "San Francisco, CA",
  "remoteType": "hybrid",
  "salary": "$150k-$200k",
  "description": "We are looking for...",
  "requirements": "[\"Python\", \"Django\", \"PostgreSQL\", \"AWS\"]",
  "jobType": "Full-time",
  "postedDate": "2025-11-01",
  "applicationUrl": "https://techcorp.com/careers/apply/123",
  "scrapedAt": "2025-11-10T18:00:00Z",
  "scrapeSuccess": true
}
```

**Error Responses:**
- `404 Not Found` - Job not found

---

### GET /jobs/search?q={query}
Search for jobs by keyword.

**Query Parameters:**
- `q` - Search query (searches in title, company, location, description)

**Example:** `/jobs/search?q=python`

**Response (200 OK):**
```json
{
  "total": 25,
  "jobs": [
    {
      "id": 1,
      "title": "Python Developer",
      "company": "TechCorp",
      "..."
    }
  ]
}
```

---

## Job Matching Endpoints

### GET /users/{email}/matches?limit={limit}
Get AI-powered job matches for a user based on their skills.

**Path Parameters:**
- `email` - User's email address

**Query Parameters:**
- `limit` - Maximum number of matches to return (default: 10)

**Example:** `/users/john@example.com/matches?limit=5`

**Response (200 OK):**
```json
{
  "total": 85,
  "matches": [
    {
      "jobInfo": {
        "id": 1,
        "title": "Senior Backend Engineer",
        "company": "TechCorp",
        "..."
      },
      "matchScore": 85.7,
      "matchedSkills": ["Python", "Django", "PostgreSQL", "AWS", "Docker", "Git"],
      "missingSkills": ["Kubernetes"],
      "explanation": "✓ You have 6 matching skill(s): Python, Django, PostgreSQL, AWS, Docker, Git\n✗ Missing 1 skill(s): Kubernetes"
    }
  ]
}
```

**Error Responses:**
- `400 Bad Request` - User has no skills parsed
- `404 Not Found` - User not found

---

## Application Endpoints

### POST /applications
Record a job application.

**Request Body:**
```json
{
  "email": "john@example.com",
  "jobInfoId": 1,
  "notes": "Applied via company website"
}
```

**Response (201 Created):**
```json
{
  "message": "Application created successfully"
}
```

**Error Responses:**
- `400 Bad Request` - Missing required fields
- `404 Not Found` - User not found
- `409 Conflict` - Already applied to this job

---

### GET /users/{email}/applications?status={status}
Get all applications for a user.

**Path Parameters:**
- `email` - User's email address

**Query Parameters:**
- `status` - Filter by status (optional): `pending`, `applied`, `interviewing`, `rejected`, `accepted`

**Example:** `/users/john@example.com/applications?status=pending`

**Response (200 OK):**
```json
{
  "total": 15,
  "applications": [
    {
      "id": 1,
      "userId": 1,
      "jobInfoId": 42,
      "status": "pending",
      "appliedAt": "2025-11-10T18:00:00Z",
      "notes": "Applied via company website",
      "resumeVersion": null
    }
  ]
}
```

---

### PATCH /applications/{id}/status
Update the status of an application.

**Path Parameters:**
- `id` - Application ID

**Request Body:**
```json
{
  "status": "interviewing",
  "notes": "First round interview scheduled for Nov 15"
}
```

**Valid statuses:** `pending`, `applied`, `interviewing`, `rejected`, `accepted`

**Response (200 OK):**
```json
{
  "message": "Application updated successfully"
}
```

**Error Responses:**
- `400 Bad Request` - Missing or invalid status
- `404 Not Found` - Application not found

---

### GET /users/{email}/applications/stats
Get application statistics for a user.

**Path Parameters:**
- `email` - User's email address

**Response (200 OK):**
```json
{
  "stats": [
    {
      "status": "pending",
      "count": 5
    },
    {
      "status": "applied",
      "count": 8
    },
    {
      "status": "interviewing",
      "count": 2
    }
  ]
}
```

---

## Starting the API Server

```bash
# Default port (8080)
java -jar target/link-collector-0.1.0.jar api-server

# Custom port
java -jar target/link-collector-0.1.0.jar api-server 3000
```

## Environment Variables

Create a `.env` file in the project root:

```
OPENAI_API_KEY=sk-your-api-key-here
JOBS_DB_URL=jdbc:sqlite:jobs.db
HEADLESS=true
```

## Error Response Format

All error responses follow this format:

```json
{
  "error": "Error message description"
}
```

Common HTTP status codes:
- `200` - OK
- `201` - Created
- `400` - Bad Request (invalid input)
- `401` - Unauthorized (invalid credentials)
- `404` - Not Found
- `409` - Conflict (duplicate resource)
- `500` - Internal Server Error
