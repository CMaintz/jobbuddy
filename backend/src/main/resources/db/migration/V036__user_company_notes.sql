-- Company research notes become per user.
--
-- V033 put the notes on the shared companies row, so every user read and overwrote the same text
-- (and it grounded everyone's cover letters). They now live in their own table keyed by
-- (user_id, company_id).

CREATE TABLE user_company_notes (
    user_id uuid NOT NULL,
    company_id uuid NOT NULL,
    notes text NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT user_company_notes_pkey PRIMARY KEY (user_id, company_id),
    CONSTRAINT user_company_notes_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT user_company_notes_company_id_fkey FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
);
CREATE INDEX idx_user_company_notes_company ON public.user_company_notes USING btree (company_id);

-- Existing notes have no author. Hand a note to a user only when exactly one user has an application
-- or a tracked outreach for that company; anything else is ambiguous and is dropped, because copying
-- it to several users would keep the leak this migration exists to close.
WITH candidates AS (
    SELECT a.user_id, j.company_id
    FROM applications a
    JOIN jobs j ON j.id = a.job_id
    WHERE j.company_id IS NOT NULL
    UNION
    SELECT o.user_id, o.company_id
    FROM outreach_contact o
    WHERE o.company_id IS NOT NULL
),
sole_owner AS (
    SELECT company_id, min(user_id::text)::uuid AS user_id
    FROM candidates
    GROUP BY company_id
    HAVING count(DISTINCT user_id) = 1
)
INSERT INTO user_company_notes (user_id, company_id, notes, updated_at)
SELECT s.user_id, c.id, c.research_notes, coalesce(c.research_notes_updated_at, now())
FROM companies c
JOIN sole_owner s ON s.company_id = c.id
WHERE c.research_notes IS NOT NULL AND btrim(c.research_notes) <> '';

ALTER TABLE companies DROP COLUMN research_notes;
ALTER TABLE companies DROP COLUMN research_notes_updated_at;
