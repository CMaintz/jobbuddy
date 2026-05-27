-- V026: Split PII from profile into a dedicated table
-- ──────────────────────────────────────────────────────────────────────────────

-- 1. Create separate PII table
CREATE TABLE profile_private_info (
    id              UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID        NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    full_name       TEXT,
    phone           TEXT,
    photo_url       TEXT,
    location        TEXT,
    municipality    TEXT,
    contact_email   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_profile_private_info_user ON profile_private_info(user_id);

-- 2. Migrate existing PII data from profile
INSERT INTO profile_private_info (user_id, full_name, phone, photo_url, location, municipality)
SELECT user_id, full_name, phone, photo_url, location, municipality
FROM profiles
ON CONFLICT (user_id) DO NOTHING;

-- 3. Remove PII columns from profiles table
ALTER TABLE profiles
    DROP COLUMN IF EXISTS full_name,
    DROP COLUMN IF EXISTS phone,
    DROP COLUMN IF EXISTS photo_url,
    DROP COLUMN IF EXISTS location,
    DROP COLUMN IF EXISTS municipality;
