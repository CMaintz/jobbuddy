-- Skills.
--
-- One representation, whatever the source. A skill name resolves against skill_taxonomy, and the
-- row it lands on supplies the category that decides how the CV groups it and whether it counts as
-- a technology. Imported CVs and hand-typed skills both land here, so a skill's attributes never
-- depend on how it arrived.

CREATE TABLE skill_taxonomy (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    name character varying(200) NOT NULL,
    normalized_name character varying(200) NOT NULL,
    parent_id uuid,
    category character varying(100),
    aliases text[],
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT skill_taxonomy_pkey PRIMARY KEY (id),
    CONSTRAINT skill_taxonomy_name_key UNIQUE (name),
    CONSTRAINT skill_taxonomy_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES skill_taxonomy(id)
);
CREATE INDEX idx_skill_taxonomy_category ON public.skill_taxonomy USING btree (category);
CREATE INDEX idx_skill_taxonomy_normalized ON public.skill_taxonomy USING btree (normalized_name);

CREATE TABLE profile_skills (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    skill_name character varying(200) NOT NULL,
    taxonomy_id uuid,
    proficiency_level character varying(50) DEFAULT 'INTERMEDIATE'::character varying,
    years_experience integer,
    used_in_production boolean DEFAULT false,
    display_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    category character varying(100),
    CONSTRAINT profile_skills_pkey PRIMARY KEY (id),
    CONSTRAINT unique_user_skill UNIQUE (user_id, skill_name),
    CONSTRAINT profile_skills_taxonomy_id_fkey FOREIGN KEY (taxonomy_id) REFERENCES skill_taxonomy(id),
    CONSTRAINT profile_skills_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_profile_skills_category ON public.profile_skills USING btree (category);
CREATE INDEX idx_profile_skills_user_id ON public.profile_skills USING btree (user_id);
