-- Separate "protected" from "default", which is_system was conflating.
--
-- is_system meant three things at once: the app shipped it, the user may not edit it, and it is
-- a candidate for being the default. Those are different properties, and merging them meant the
-- only way to stop using a seeded persona was to have an admin edit app-owned content.
--
--   is_protected — the app wrote it, so the user cannot edit or delete it. They can duplicate it
--                  and edit the copy, which is what "customise the house prompt" should mean.
--   is_default   — the seeded starting point for its category. Still app data.
--   user_default_prompt — which template a given user actually wants for a category. Switching
--                  the default is now the user's own row rather than a write to app-owned content.

ALTER TABLE prompt_templates ADD COLUMN IF NOT EXISTS is_protected BOOLEAN NOT NULL DEFAULT FALSE;

-- Everything the app seeded is protected; everything a user wrote is not.
UPDATE prompt_templates SET is_protected = TRUE WHERE is_system = TRUE;

COMMENT ON COLUMN prompt_templates.is_protected IS
  'App-origin: the user may duplicate it but never edit or delete it.';
COMMENT ON COLUMN prompt_templates.is_default IS
  'The seeded starting point for this category. Per-user overrides live in user_default_prompt.';

CREATE TABLE user_default_prompt (
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category    VARCHAR(60) NOT NULL,
    template_id UUID        NOT NULL REFERENCES prompt_templates(id) ON DELETE CASCADE,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, category)
);

CREATE INDEX idx_user_default_prompt_template ON user_default_prompt(template_id);
