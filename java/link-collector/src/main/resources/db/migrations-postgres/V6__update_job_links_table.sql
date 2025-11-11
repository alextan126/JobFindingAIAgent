-- Add new column to track when job was scraped (PostgreSQL version)
ALTER TABLE job_links ADD COLUMN IF NOT EXISTS scraped_at TIMESTAMP;

-- Status values: 'new' (discovered, not scraped), 'scraped' (successfully scraped), 'error' (scraping failed)
