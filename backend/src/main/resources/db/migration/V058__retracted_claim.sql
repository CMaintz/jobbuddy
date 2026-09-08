-- Claims the user has explicitly disowned. Generated candidate-facing content is checked
-- against these so a retracted claim can never resurface.
CREATE TABLE retracted_claim (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    claim      TEXT NOT NULL,
    reason     TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_retracted_claim_user ON retracted_claim (user_id);
