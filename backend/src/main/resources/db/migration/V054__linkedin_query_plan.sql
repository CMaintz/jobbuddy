-- Per-user LinkedIn search keyword plans, generated from the profile by the LLM planner.
-- The connector crosses these keywords with the configured target locations.
CREATE TABLE linkedin_query_plan (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    keywords     TEXT[] NOT NULL DEFAULT '{}',
    breadth      VARCHAR(20),
    generated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_linkedin_query_plan_user ON linkedin_query_plan (user_id);
