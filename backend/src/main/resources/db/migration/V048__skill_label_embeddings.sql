-- Meaning-based skill matching and adjacent-skill suggestions (#350).
--
-- skill_label_embeddings caches one vector per skill label and embedding model, shared by every
-- user: matching compares a posting's asks with the profile's skills by meaning, and a label is
-- embedded once, not once per feed. A plain real[] rather than vector: the rows are only ever
-- read back by key and compared in memory, never searched by nearest neighbour.
--
-- adjacent_skill_runs remembers which skill set a user was last asked about, so saving a profile
-- whose skills did not change does not ask the AI for the same suggestions again.

CREATE TABLE skill_label_embeddings (
    model character varying(100) NOT NULL,
    label character varying(200) NOT NULL,
    embedding real[] NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT skill_label_embeddings_pkey PRIMARY KEY (model, label)
);

CREATE TABLE adjacent_skill_runs (
    user_id uuid NOT NULL,
    skills_fingerprint character varying(64) NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT adjacent_skill_runs_pkey PRIMARY KEY (user_id),
    CONSTRAINT adjacent_skill_runs_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
