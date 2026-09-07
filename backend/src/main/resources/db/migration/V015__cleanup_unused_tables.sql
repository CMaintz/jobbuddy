-- Drop tables that were scaffolded but never wired to any entity or service.
-- job_sources: superseded by the jobs.source enum column
-- job_tags:    superseded by the jobs.ai_tags text[] column
-- interview_metrics: not implemented; response_metrics covers the same use case

DROP TABLE IF EXISTS interview_metrics;
DROP TABLE IF EXISTS job_tags;
DROP TABLE IF EXISTS job_sources;

-- Index on jobs.url to support fast URL-lookup for manual job deduplication
CREATE INDEX IF NOT EXISTS idx_jobs_url ON jobs(url);
