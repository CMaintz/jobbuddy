-- V030: Add recruiter_reply column to applications
-- Stores the recruiter's response to outreach messages, used as AI context
-- when generating application documents.
-- ──────────────────────────────────────────────────────────────────────────────

ALTER TABLE applications ADD COLUMN recruiter_reply TEXT;
