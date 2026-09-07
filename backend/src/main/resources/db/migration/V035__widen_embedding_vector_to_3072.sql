-- gemini-embedding-001 produces 3072-dimensional vectors.
-- The original column was sized for OpenAI text-embedding-3-small (1536).
-- Existing embeddings are crawler-generated and can be regenerated, so we
-- drop and re-add rather than trying to cast.
ALTER TABLE job_embeddings DROP COLUMN embedding;
ALTER TABLE job_embeddings ADD COLUMN embedding vector(3072) NOT NULL;
