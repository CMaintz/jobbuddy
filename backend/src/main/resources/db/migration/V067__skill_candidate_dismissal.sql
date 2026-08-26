-- Skills the user was offered and declined.
--
-- Without this, every visit re-suggests the same rejects and the feature reads as nagging. A
-- dismissal is per skill NAME rather than per taxonomy row, because the strongest candidates come
-- from job postings and are often not in the taxonomy at all.
CREATE TABLE skill_candidate_dismissal (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    normalized_name TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_skill_candidate_dismissal_user_name
    ON skill_candidate_dismissal (user_id, normalized_name);
