-- The one embedding provider and model the install embeds with.
--
-- The desktop app embeds her profile and searches, the scheduled crawl embeds postings, and
-- vectors from two models cannot be compared. Desktop setup writes this row; every process reads
-- it at startup and embeds with it. One row only, enforced by the key.

CREATE TABLE embedding_choice (
    id smallint DEFAULT 1 NOT NULL,
    provider character varying(20) NOT NULL,
    model character varying(100) NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT embedding_choice_pkey PRIMARY KEY (id),
    CONSTRAINT embedding_choice_single_row CHECK (id = 1),
    CONSTRAINT embedding_choice_provider CHECK (provider IN ('openai', 'gemini'))
);

-- Neighbour queries now filter on the model; without this they scan every model's rows.
CREATE INDEX idx_job_embeddings_model ON job_embeddings (model);
