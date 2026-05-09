-- Extend preferences with job filter fields
ALTER TABLE preferences ADD COLUMN IF NOT EXISTS preferred_remote_types text[];
ALTER TABLE preferences ADD COLUMN IF NOT EXISTS preferred_employment_types text[];
ALTER TABLE preferences ADD COLUMN IF NOT EXISTS preferred_seniority text[];
ALTER TABLE preferences ADD COLUMN IF NOT EXISTS salary_min INT;
ALTER TABLE preferences ADD COLUMN IF NOT EXISTS salary_max INT;

-- Index for faster ignored job lookups
CREATE INDEX IF NOT EXISTS idx_ignored_jobs_user_id ON ignored_jobs(user_id);
