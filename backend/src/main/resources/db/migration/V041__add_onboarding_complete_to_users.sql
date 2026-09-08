ALTER TABLE users ADD COLUMN onboarding_complete BOOLEAN NOT NULL DEFAULT FALSE;
-- Existing users have already set up their profiles — skip the wizard for them
UPDATE users SET onboarding_complete = TRUE;
