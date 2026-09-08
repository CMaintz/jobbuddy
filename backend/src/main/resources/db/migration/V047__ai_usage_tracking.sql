-- Track per-user AI API usage for cost visibility and budget caps.
CREATE TABLE ai_usage_log (
    id          UUID                     NOT NULL DEFAULT gen_random_uuid(),
    user_id     UUID                     NOT NULL,
    model       VARCHAR(100)             NOT NULL,
    tokens_in   INT                      NOT NULL DEFAULT 0,
    tokens_out  INT                      NOT NULL DEFAULT 0,
    operation   VARCHAR(50)              NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_ai_usage_log PRIMARY KEY (id),
    CONSTRAINT fk_ai_usage_log_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_ai_usage_log_user_date ON ai_usage_log (user_id, created_at);
