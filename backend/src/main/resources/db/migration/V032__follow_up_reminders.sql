CREATE TABLE follow_up_reminders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID NOT NULL,
    user_id UUID NOT NULL,
    note TEXT,
    due_at TIMESTAMPTZ NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_follow_up_reminders_app ON follow_up_reminders(application_id);
CREATE INDEX idx_follow_up_reminders_user_due ON follow_up_reminders(user_id, due_at) WHERE completed = FALSE;
