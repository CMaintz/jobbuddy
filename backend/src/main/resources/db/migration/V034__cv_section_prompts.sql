-- Per-user standing instructions for CV tailoring, one row per user.
--
-- "How I want my profile / competencies / experience written." Injected into the CV tailoring
-- prompt beside each section and reused by the per-field refine. Authored by the user and never
-- overwritten by the app (unlike writing_profiles, which the style analyser recomputes).

CREATE TABLE cv_section_prompts (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    prompts jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT cv_section_prompts_pkey PRIMARY KEY (id),
    CONSTRAINT cv_section_prompts_user_id_key UNIQUE (user_id),
    CONSTRAINT cv_section_prompts_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
