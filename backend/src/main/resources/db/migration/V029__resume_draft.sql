-- V029: Resume draft persistence (server-side auto-save)
-- ──────────────────────────────────────────────────────────────────────────────

CREATE TABLE resume_draft (
    id              UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name            TEXT        NOT NULL DEFAULT 'My Resume',
    job_id          UUID        REFERENCES jobs(id) ON DELETE SET NULL,
    application_id  UUID        REFERENCES applications(id) ON DELETE SET NULL,
    resume_data     JSONB       NOT NULL DEFAULT '{}',   -- ResumeData JSON blob
    settings        JSONB       NOT NULL DEFAULT '{}',   -- ResumeSettings JSON blob
    status          TEXT        NOT NULL DEFAULT 'DRAFT', -- 'DRAFT' | 'PUBLISHED'
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_resume_draft_user        ON resume_draft(user_id);
CREATE INDEX idx_resume_draft_application ON resume_draft(application_id);
CREATE INDEX idx_resume_draft_job         ON resume_draft(job_id);
