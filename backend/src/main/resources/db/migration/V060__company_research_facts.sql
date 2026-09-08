-- Cached, AI-extracted verified facts about a company (from its own website), used to
-- ground cover-letter references. Cached to avoid re-fetching/re-extracting on every generation.
ALTER TABLE companies ADD COLUMN researched_facts TEXT;
ALTER TABLE companies ADD COLUMN facts_researched_at TIMESTAMPTZ;
