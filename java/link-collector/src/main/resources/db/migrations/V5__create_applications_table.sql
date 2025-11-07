-- Applications table to track user job applications
CREATE TABLE IF NOT EXISTS applications (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id         INTEGER NOT NULL,
    job_info_id     INTEGER NOT NULL,
    status          TEXT NOT NULL DEFAULT 'pending',  -- pending, applied, interviewing, rejected, accepted
    applied_at      TEXT NOT NULL,
    notes           TEXT,
    resume_version  TEXT,

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (job_info_id) REFERENCES job_info(id) ON DELETE CASCADE,

    -- Prevent duplicate applications
    UNIQUE(user_id, job_info_id)
);

-- Indexes for common queries
CREATE INDEX IF NOT EXISTS idx_applications_user ON applications(user_id);
CREATE INDEX IF NOT EXISTS idx_applications_job ON applications(job_info_id);
CREATE INDEX IF NOT EXISTS idx_applications_status ON applications(status);
CREATE INDEX IF NOT EXISTS idx_applications_applied_at ON applications(applied_at);
