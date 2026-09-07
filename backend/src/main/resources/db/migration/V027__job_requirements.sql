-- What a posting asks for, in its own words.
--
-- required_skills/preferred_skills are narrowed to labels the posting also listed under
-- technologies or skills. That protects the matcher from a hallucinated requirement, but
-- it drops everything that is not a short label — "5 års erfaring med backend", "dansk på
-- forhandlingsniveau", "kørekort" — which is most of what a posting actually demands. The
-- CV tailoring never saw those, so it never answered them.
--
-- Stored as jsonb rather than a child table: they are always read with the job, never
-- queried across jobs, and the shape is still settling.

ALTER TABLE jobs
    ADD COLUMN requirements jsonb DEFAULT '[]'::jsonb NOT NULL;
