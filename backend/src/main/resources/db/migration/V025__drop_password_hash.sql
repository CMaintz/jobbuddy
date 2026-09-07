-- password_hash is unused: Firebase is the sole auth provider.
-- The column was already made nullable in V016; now dropping it entirely.
ALTER TABLE users DROP COLUMN IF EXISTS password_hash;
