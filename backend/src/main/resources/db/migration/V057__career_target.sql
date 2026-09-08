-- Per-user career targeting / North-Star: the persistent layer behind archetype-aware
-- generation. Declared target archetypes, a North-Star statement, a positioning narrative,
-- and culture requirements feed the AI context (identity-free) and job evaluation.
CREATE TABLE career_target (
    user_id              UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    target_archetypes    TEXT[] NOT NULL DEFAULT '{}',
    north_star           TEXT,
    narrative            TEXT,
    culture_requirements TEXT[] NOT NULL DEFAULT '{}',
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
