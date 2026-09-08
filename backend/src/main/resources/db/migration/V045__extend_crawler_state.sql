-- Extend crawler_state to track crawl outcomes for admin observability.
ALTER TABLE crawler_state
    ADD COLUMN last_crawl_started_at  TIMESTAMP WITH TIME ZONE,
    ADD COLUMN last_crawl_finished_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN jobs_found             INT NOT NULL DEFAULT 0,
    ADD COLUMN jobs_ingested          INT NOT NULL DEFAULT 0,
    ADD COLUMN last_error             TEXT,
    ADD COLUMN is_running             BOOLEAN NOT NULL DEFAULT FALSE;
