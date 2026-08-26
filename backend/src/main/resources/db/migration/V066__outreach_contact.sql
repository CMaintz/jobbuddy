-- Tracking for unsolicited outreach.
--
-- A ranked target had no state until an application existed, so nothing recorded who had been
-- contacted or when to come back — and the follow-up is most of what makes unsolicited contact
-- work. This is deliberately separate from `applications`: there is no posting and no vacancy to
-- apply to, so forcing it into the application pipeline would mean inventing a job that does not
-- exist.
CREATE TABLE outreach_contact (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id       UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    company_id    UUID REFERENCES companies (id) ON DELETE SET NULL,
    -- Kept alongside company_id so a saved target survives the company row being cleaned up.
    company_name  TEXT NOT NULL,
    status        TEXT NOT NULL DEFAULT 'SAVED',
    channel       TEXT,
    contact_name  TEXT,
    contacted_at  TIMESTAMPTZ,
    follow_up_due DATE,
    notes         TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One tracked outreach per company per user: saving the same target twice is an update, not a
-- second thread of contact.
CREATE UNIQUE INDEX idx_outreach_contact_user_company
    ON outreach_contact (user_id, company_id) WHERE company_id IS NOT NULL;

CREATE INDEX idx_outreach_contact_user_followup
    ON outreach_contact (user_id, follow_up_due) WHERE follow_up_due IS NOT NULL;
