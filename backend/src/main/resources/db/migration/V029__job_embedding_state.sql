-- Real embedding state, for the same reason enrichment got one.
--
-- Embedding was fired from the ingest path only. Anything the enrichment sweep rescued was
-- enriched and never embedded, so it looked complete in the database while being invisible to
-- every consumer of the vector index: the recommendation feed, semantic search, similar jobs,
-- and the market corpus behind skill candidates. Absence of a job_embeddings row could not
-- distinguish "never started" from "in flight" from "failed", so nothing could safely retry it.

ALTER TABLE jobs
    ADD COLUMN embedding_status character varying(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN embedding_attempts integer NOT NULL DEFAULT 0,
    ADD COLUMN embedding_last_attempt_at timestamp with time zone,
    ADD COLUMN embedding_last_error text;

-- The backfill is this statement. Anything with a vector already is EMBEDDED; everything else
-- stays PENDING and the worker picks it up, which is exactly the set that went missing.
UPDATE jobs SET embedding_status = 'EMBEDDED'
WHERE EXISTS (SELECT 1 FROM job_embeddings e WHERE e.job_id = jobs.id);

-- The worker asks for the oldest unembedded jobs, so index exactly that.
CREATE INDEX idx_jobs_embedding_queue
    ON jobs (embedding_last_attempt_at NULLS FIRST, created_at)
    WHERE embedding_status <> 'EMBEDDED';
