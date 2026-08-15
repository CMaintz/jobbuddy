-- Reusable interview story bank in STAR+R form (Situation, Task, Action, Result, Reflection).
-- The interview analogue of the writing-style memory: accumulated, curated stories the AI draws on.
CREATE TABLE interview_story (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title       TEXT NOT NULL,
    situation   TEXT,
    task        TEXT,
    action      TEXT,
    result      TEXT,
    reflection  TEXT,
    tags        TEXT[] NOT NULL DEFAULT '{}',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_interview_story_user ON interview_story (user_id);
