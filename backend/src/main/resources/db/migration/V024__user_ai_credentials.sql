-- A user's own generation API key.
--
-- One row per user at most. The key is stored encrypted (AES-GCM, base64) and is
-- never returned to a client; the UI shows only the provider and the last four
-- characters.

CREATE TABLE user_ai_credentials (
    user_id uuid NOT NULL,
    provider character varying(20) NOT NULL,
    api_key_encrypted text NOT NULL,
    -- Optional model override; null means the provider's configured default.
    model character varying(100),
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT pk_user_ai_credentials PRIMARY KEY (user_id),
    CONSTRAINT fk_user_ai_credentials_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
