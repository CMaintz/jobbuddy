-- Skill suggestions.
--
-- Skills a document evidences without naming, queued for the user to confirm, and the ones they
-- have declined so they are never offered again. The parsers stay strictly extractive; an inference
-- is a question, not a fact.

CREATE TABLE parsed_skill_suggestion (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    skill_name character varying(200) NOT NULL,
    normalized_name character varying(200) NOT NULL,
    evidence text,
    source character varying(40) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT parsed_skill_suggestion_pkey PRIMARY KEY (id),
    CONSTRAINT unique_user_parsed_skill UNIQUE (user_id, normalized_name),
    CONSTRAINT parsed_skill_suggestion_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_parsed_skill_suggestion_user ON public.parsed_skill_suggestion USING btree (user_id);

CREATE TABLE skill_candidate_dismissal (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    normalized_name text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT skill_candidate_dismissal_pkey PRIMARY KEY (id),
    CONSTRAINT skill_candidate_dismissal_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX idx_skill_candidate_dismissal_user_name ON public.skill_candidate_dismissal USING btree (user_id, normalized_name);
