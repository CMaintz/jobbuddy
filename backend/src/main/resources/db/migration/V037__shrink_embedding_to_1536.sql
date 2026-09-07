-- V037: Shrink job_embeddings.embedding back to 1536 dimensions.
-- V035 widened it to 3072 for Gemini. We have since switched back to
-- OpenAI text-embedding-3-small (1536 dims).
-- Truncate existing embeddings first (they must be regenerated at 1536 dims).
TRUNCATE TABLE job_embeddings;
ALTER TABLE job_embeddings DROP COLUMN embedding;
ALTER TABLE job_embeddings ADD COLUMN embedding vector(1536) NOT NULL;
