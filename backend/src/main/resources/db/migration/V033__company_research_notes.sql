-- User-supplied company research.
--
-- Free-text research the candidate pastes in (typically produced by an external agent) to ground a
-- cover letter's company references. Distinct from researched_facts: those are AI-extracted from the
-- company's own site (verified), these are user-curated (semi-trusted) — the prompt labels them apart.

ALTER TABLE companies ADD COLUMN research_notes text;
ALTER TABLE companies ADD COLUMN research_notes_updated_at timestamp with time zone;
