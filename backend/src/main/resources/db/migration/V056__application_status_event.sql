-- Append-only ledger of application status transitions. Every status change writes one row,
-- enabling funnel-velocity / time-in-stage analytics beyond the current status alone.
CREATE TABLE application_status_event (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID NOT NULL REFERENCES applications (id) ON DELETE CASCADE,
    user_id        UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    from_status    VARCHAR(40),
    to_status      VARCHAR(40) NOT NULL,
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_status_event_user ON application_status_event (user_id, occurred_at);
CREATE INDEX idx_status_event_app ON application_status_event (application_id, occurred_at);
