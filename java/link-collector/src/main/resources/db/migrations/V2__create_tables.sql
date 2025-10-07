-- idempotent DDL
CREATE TABLE IF NOT EXISTS job_links (
                                         id              INTEGER PRIMARY KEY AUTOINCREMENT,
                                         url             TEXT NOT NULL UNIQUE,
                                         host_type       TEXT NOT NULL,
                                         source          TEXT,
                                         discovered_at   TEXT NOT NULL,
                                         status          TEXT NOT NULL DEFAULT 'new',
                                         last_checked_at TEXT,
                                         last_error      TEXT
);

CREATE TABLE IF NOT EXISTS job_posts (
                                         id               INTEGER PRIMARY KEY AUTOINCREMENT,
                                         url              TEXT NOT NULL UNIQUE,
                                         platform         TEXT,
                                         title            TEXT,
                                         company          TEXT,
                                         location         TEXT,
                                         apply_url        TEXT,
                                         description_text TEXT,
                                         scraped_at       TEXT NOT NULL,
                                         http_status      INTEGER
);

CREATE INDEX IF NOT EXISTS idx_job_links_status   ON job_links(status);
CREATE INDEX IF NOT EXISTS idx_job_links_host     ON job_links(host_type);
CREATE INDEX IF NOT EXISTS idx_job_posts_platform ON job_posts(platform);
