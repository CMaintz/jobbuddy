-- Caches pre-computed profile embeddings so recommendation lookups
-- don't call the AI provider on every request.
CREATE TABLE profile_embeddings (
    id         UUID                     NOT NULL DEFAULT gen_random_uuid(),
    user_id    UUID                     NOT NULL UNIQUE,
    embedding  vector(1536)             NOT NULL,
    model      VARCHAR(100)             NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_profile_embeddings PRIMARY KEY (id),
    CONSTRAINT fk_profile_embeddings_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
