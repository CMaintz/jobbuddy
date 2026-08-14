-- SimHash content fingerprint for near-duplicate / cross-listing detection.
-- Jobs sharing a fingerprint are the same posting re-listed (e.g. an agency re-posting
-- a role under a different company/URL) and get clustered via duplicate_group_id.
ALTER TABLE jobs ADD COLUMN content_fingerprint BIGINT;

CREATE INDEX idx_jobs_content_fingerprint
    ON jobs (content_fingerprint)
    WHERE content_fingerprint IS NOT NULL;
