-- Users table for authentication and profile (PostgreSQL version)
CREATE TABLE IF NOT EXISTS users (
    id                SERIAL PRIMARY KEY,
    email             TEXT NOT NULL UNIQUE,
    password_hash     TEXT NOT NULL,
    full_name         TEXT NOT NULL,
    resume_path       TEXT,
    resume_text       TEXT,
    skills            JSONB,  -- JSON format: ["Java", "Python", "SQL", ...]
    preferences       JSONB,  -- JSON format: {"desired_salary": "80k-100k", "locations": ["SF", "NYC"], "job_types": ["full-time"]}
    graduation_date   TEXT,
    experience_level  TEXT,  -- entry, mid, senior
    created_at        TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP NOT NULL
);

-- Index for email lookups (login)
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
