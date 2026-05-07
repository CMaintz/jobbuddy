CREATE TABLE application_metrics (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_applications INT DEFAULT 0,
    total_saved INT DEFAULT 0,
    total_ignored INT DEFAULT 0,
    response_rate NUMERIC(5, 2),
    interview_rate NUMERIC(5, 2),
    offer_rate NUMERIC(5, 2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE interview_metrics (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    application_id UUID NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    interview_type VARCHAR(100),
    scheduled_at TIMESTAMPTZ,
    outcome VARCHAR(100),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE response_metrics (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    job_id UUID REFERENCES jobs(id) ON DELETE SET NULL,
    application_id UUID REFERENCES applications(id) ON DELETE SET NULL,
    event_type VARCHAR(100) NOT NULL,
    event_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    metadata JSONB
);
