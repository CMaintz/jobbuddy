-- V050: Prompt template quality-of-life — tags, usage counts, per-user favourites.
ALTER TABLE prompt_templates ADD COLUMN tags TEXT[] NOT NULL DEFAULT '{}';
ALTER TABLE prompt_templates ADD COLUMN usage_count INT NOT NULL DEFAULT 0;

-- Favourites are per-user (system templates can be favourited too), hence a junction table.
CREATE TABLE prompt_template_favorites (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    template_id UUID NOT NULL REFERENCES prompt_templates(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, template_id)
);

CREATE INDEX idx_prompt_favorites_user ON prompt_template_favorites (user_id);
