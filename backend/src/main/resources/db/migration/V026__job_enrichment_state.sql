-- Real enrichment state, replacing "ai_summary IS NULL" as the proxy for "not done yet".
--
-- The proxy had three failure modes. A job whose enrichment ran but produced no summary
-- was never marked done and came back on every sweep, forever, spending quota each time.
-- A job whose summary landed but whose skills, contact and deadline all failed counted as
-- finished and was never revisited. And nothing recorded how many times a posting had been
-- tried, so one that can never succeed was retried indefinitely with no way to see it.

ALTER TABLE jobs
    ADD COLUMN enrichment_status character varying(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN enrichment_attempts integer NOT NULL DEFAULT 0,
    ADD COLUMN enrichment_last_attempt_at timestamp with time zone,
    -- Why the last attempt failed, for looking at a stuck posting without the logs.
    ADD COLUMN enrichment_last_error text;

-- Anything already carrying a summary was enriched under the old rules; anything else
-- is pending and the sweep will pick it up.
UPDATE jobs SET enrichment_status = 'ENRICHED' WHERE ai_summary IS NOT NULL;

-- The sweep asks for the oldest unfinished jobs, so index exactly that.
CREATE INDEX idx_jobs_enrichment_queue
    ON jobs (enrichment_last_attempt_at NULLS FIRST, created_at)
    WHERE enrichment_status <> 'ENRICHED';
