-- Enhanced job information table (replaces/extends job_posts concept)
-- Most fields are nullable since job postings vary widely in what they display
CREATE TABLE IF NOT EXISTS job_info (
    -- Required fields
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    job_link_id      INTEGER NOT NULL UNIQUE,
    scraped_at       TEXT NOT NULL,
    scrape_success   INTEGER NOT NULL DEFAULT 1,  -- 1 = success, 0 = failure

    -- Core job details (nullable - not all postings have all fields)
    title            TEXT,
    company          TEXT,
    location         TEXT,
    remote_type      TEXT,  -- 'remote', 'hybrid', 'onsite', NULL = unknown

    -- Compensation (nullable - rarely posted)
    salary           TEXT,

    -- Job content (nullable)
    description      TEXT,
    requirements     TEXT,  -- JSON format: {"skills": [], "qualifications": [], "responsibilities": []}

    -- Job classification (nullable)
    job_type         TEXT CHECK(job_type IN ('full-time', 'part-time', 'internship', 'contract', 'temporary', 'other') OR job_type IS NULL),

    -- Metadata (nullable)
    posted_date      TEXT,
    application_url  TEXT,

    FOREIGN KEY (job_link_id) REFERENCES job_links(id) ON DELETE CASCADE
);

-- Indexes for common queries
CREATE INDEX IF NOT EXISTS idx_job_info_job_link ON job_info(job_link_id);
CREATE INDEX IF NOT EXISTS idx_job_info_company ON job_info(company);
CREATE INDEX IF NOT EXISTS idx_job_info_location ON job_info(location);
CREATE INDEX IF NOT EXISTS idx_job_info_remote_type ON job_info(remote_type);
CREATE INDEX IF NOT EXISTS idx_job_info_job_type ON job_info(job_type);
CREATE INDEX IF NOT EXISTS idx_job_info_scrape_success ON job_info(scrape_success);
