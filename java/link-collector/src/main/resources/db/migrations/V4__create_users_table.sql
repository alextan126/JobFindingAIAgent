-- Users table for authentication and profile
CREATE TABLE IF NOT EXISTS users (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    email             TEXT NOT NULL UNIQUE,
    password_hash     TEXT NOT NULL,
    full_name         TEXT NOT NULL,
    resume_path       TEXT,
    resume_text       TEXT,
    skills            TEXT,  -- JSON format: ["Java", "Python", "SQL", ...]
    preferences       TEXT,  -- JSON format: {"desired_salary": "80k-100k", "locations": ["SF", "NYC"], "job_types": ["full-time"]}
    graduation_date   TEXT,
    experience_level  TEXT,  -- entry, mid, senior
    created_at        TEXT NOT NULL,
    updated_at        TEXT NOT NULL
);

-- Index for email lookups (login)
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
