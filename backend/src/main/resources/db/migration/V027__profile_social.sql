-- V027: Social links as first-class entities (replaces hardcoded URL columns)
-- ──────────────────────────────────────────────────────────────────────────────

CREATE TABLE profile_social (
    id             UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id        UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform       TEXT        NOT NULL,   -- 'LinkedIn', 'GitHub', 'Website', 'Twitter', etc.
    url            TEXT        NOT NULL,
    username       TEXT,
    icon_key       TEXT        NOT NULL,   -- 'linkedin', 'github', 'globe', 'twitter', etc.
    display_order  INT         NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_profile_social_user ON profile_social(user_id);

-- Migrate existing hardcoded URL columns to the new table
INSERT INTO profile_social (user_id, platform, url, icon_key, display_order)
SELECT user_id, 'LinkedIn', linkedin_url, 'linkedin', 0
FROM profiles
WHERE linkedin_url IS NOT NULL AND linkedin_url != '';

INSERT INTO profile_social (user_id, platform, url, icon_key, display_order)
SELECT user_id, 'GitHub', github_url, 'github', 1
FROM profiles
WHERE github_url IS NOT NULL AND github_url != '';

INSERT INTO profile_social (user_id, platform, url, icon_key, display_order)
SELECT user_id, 'Website', website_url, 'globe', 2
FROM profiles
WHERE website_url IS NOT NULL AND website_url != '';

-- Remove old URL columns from profiles
ALTER TABLE profiles
    DROP COLUMN IF EXISTS linkedin_url,
    DROP COLUMN IF EXISTS github_url,
    DROP COLUMN IF EXISTS website_url;
