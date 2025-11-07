-- Add new column to track when job was scraped
ALTER TABLE job_links ADD COLUMN scraped_at TEXT;

-- Update comments for status field
-- Status values: 'new' (discovered, not scraped), 'scraped' (successfully scraped), 'error' (scraping failed)
