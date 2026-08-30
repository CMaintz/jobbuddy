-- Danish postings separate what they demand ("du skal have…", "krav") from what they would
-- like ("det er en fordel…", "gerne"). Collapsing both into jobs.skills made a nice-to-have
-- you happen to possess worth exactly as much as a must-have you lack costing nothing at all.
-- Splitting them lets the matcher weight the two differently.
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS required_skills  text[] DEFAULT '{}';
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS preferred_skills text[] DEFAULT '{}';

COMMENT ON COLUMN jobs.required_skills  IS 'Skills/technologies the posting states as a requirement.';
COMMENT ON COLUMN jobs.preferred_skills IS 'Skills/technologies the posting states as an advantage, not a requirement.';

-- jobs.skills stays as the flat union so existing readers (search indexing, gap analysis,
-- prompts) keep working; the two new columns are additive, and are empty for jobs enriched
-- before this migration until they are re-enriched.
