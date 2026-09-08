-- URL health checking for takedown detection.
-- last_url_check_at: when we last probed the posting URL.
-- url_check_failures: consecutive "looks gone" results; job is deactivated once a threshold is reached.
ALTER TABLE jobs ADD COLUMN last_url_check_at TIMESTAMPTZ;
ALTER TABLE jobs ADD COLUMN url_check_failures INT NOT NULL DEFAULT 0;

-- Partial index: the probe scheduler only ever scans active jobs, oldest-checked first.
CREATE INDEX idx_jobs_url_check ON jobs (last_url_check_at NULLS FIRST) WHERE is_active = true;
