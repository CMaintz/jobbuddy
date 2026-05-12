ALTER TABLE users ADD COLUMN linkedin_id VARCHAR(255);
CREATE UNIQUE INDEX idx_users_linkedin_id ON users(linkedin_id) WHERE linkedin_id IS NOT NULL;
