-- Prompt personas.
--
-- is_protected marks app-origin prompts: duplicable, never editable. is_default marks the seeded
-- starting point per category. Which prompt a given user actually gets is their own row in
-- user_default_prompt, so switching a default never writes to app-owned content.

CREATE TABLE prompt_templates (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid,
    name character varying(255) NOT NULL,
    category character varying(100),
    description text,
    system_prompt text,
    user_prompt text NOT NULL,
    output_constraints text,
    is_public boolean DEFAULT false,
    parent_template_id uuid,
    version_number integer DEFAULT 1 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    is_system boolean DEFAULT false NOT NULL,
    tags text[] DEFAULT '{}'::text[] NOT NULL,
    usage_count integer DEFAULT 0 NOT NULL,
    is_default boolean DEFAULT false NOT NULL,
    is_protected boolean DEFAULT false NOT NULL,
    CONSTRAINT prompt_templates_pkey PRIMARY KEY (id),
    CONSTRAINT prompt_templates_parent_template_id_fkey FOREIGN KEY (parent_template_id) REFERENCES prompt_templates(id),
    CONSTRAINT prompt_templates_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_prompt_templates_is_system ON public.prompt_templates USING btree (is_system) WHERE (is_system = true);
-- At most one seeded default per category. Without this the resolver silently fell back to
-- created_at order between two rows both claiming to be the default, which is the arbitrariness
-- is_default was introduced to remove. A per-user override lives in user_default_prompt and is
-- not constrained by this.
CREATE UNIQUE INDEX uq_prompt_templates_default_per_category ON prompt_templates (category) WHERE is_default;

CREATE TABLE prompt_template_favorites (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    template_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT prompt_template_favorites_pkey PRIMARY KEY (id),
    CONSTRAINT prompt_template_favorites_user_id_template_id_key UNIQUE (user_id, template_id),
    CONSTRAINT prompt_template_favorites_template_id_fkey FOREIGN KEY (template_id) REFERENCES prompt_templates(id) ON DELETE CASCADE,
    CONSTRAINT prompt_template_favorites_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_prompt_favorites_user ON public.prompt_template_favorites USING btree (user_id);

CREATE TABLE user_default_prompt (
    user_id uuid NOT NULL,
    category character varying(60) NOT NULL,
    template_id uuid NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT user_default_prompt_pkey PRIMARY KEY (user_id, category),
    CONSTRAINT user_default_prompt_template_id_fkey FOREIGN KEY (template_id) REFERENCES prompt_templates(id) ON DELETE CASCADE,
    CONSTRAINT user_default_prompt_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_user_default_prompt_template ON public.user_default_prompt USING btree (template_id);
