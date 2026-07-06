-- Track when a job was last seen during a crawl to enable staleness detection.
ALTER TABLE jobs ADD COLUMN last_seen_at TIMESTAMP WITH TIME ZONE;

-- Backfill existing jobs with scraped_at as initial last_seen_at
UPDATE jobs SET last_seen_at = COALESCE(scraped_at, created_at);
