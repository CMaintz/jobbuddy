-- V028: Profile strengths as first-class entities
-- ──────────────────────────────────────────────────────────────────────────────

CREATE TABLE profile_strength (
    id             UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id        UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title          TEXT        NOT NULL,
    description    TEXT,
    icon_key       TEXT        NOT NULL DEFAULT 'star',
    display_order  INT         NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_profile_strength_user ON profile_strength(user_id);
