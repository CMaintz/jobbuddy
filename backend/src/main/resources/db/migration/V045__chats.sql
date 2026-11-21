-- The desktop app's saved chats. Only the metadata lives here: each chat's messages are the
-- Claude Code session session_id, which Claude Code keeps on the user's own PC.
CREATE TABLE chats (
    id               uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id          uuid NOT NULL,
    session_id       uuid NOT NULL,
    -- NULL until her first message names the chat; the UI shows its own default meanwhile.
    title            varchar(120),
    created_at       timestamp with time zone DEFAULT now() NOT NULL,
    last_activity_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chats_pkey PRIMARY KEY (id),
    CONSTRAINT chats_session_id_key UNIQUE (session_id),
    CONSTRAINT chats_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_chats_user_activity ON chats (user_id, last_activity_at DESC);
