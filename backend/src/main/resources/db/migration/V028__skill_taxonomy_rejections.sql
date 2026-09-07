-- Labels an admin has judged not to be skills.
--
-- The taxonomy-gap queue is derived, not stored: it is recomputed from what enrichment found in
-- postings every time it is asked for. That keeps it from going stale, but it also means a label
-- the admin has already dismissed would come straight back at the top of the list. This table is
-- the only state the review needs — the "no" that sticks.
--
-- Keyed on the normalized name, the same key every other skill lookup uses, so a rejected label
-- stays rejected however the next posting happens to capitalise it.

CREATE TABLE skill_taxonomy_rejections (
    normalized_name character varying(200) NOT NULL,
    label character varying(200) NOT NULL,
    reason text,
    rejected_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT skill_taxonomy_rejections_pkey PRIMARY KEY (normalized_name)
);
