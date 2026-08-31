-- The profile.
--
-- Contact details live in profile_private_info, apart from the rest, because the profile sent to
-- the AI provider must never carry them: identity is reattached server-side after generation.
-- Skills are not here either — they are their own table, see the skills migration.

CREATE TABLE profiles (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    headline character varying(500),
    summary text,
    years_experience integer,
    languages text[],
    desired_salary_min integer,
    desired_salary_max integer,
    desired_currency character varying(10) DEFAULT 'DKK'::character varying,
    remote_preference character varying(50),
    employment_type_preference character varying(50),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    interests text[],
    CONSTRAINT profiles_pkey PRIMARY KEY (id),
    CONSTRAINT unique_user_profile UNIQUE (user_id),
    CONSTRAINT profiles_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE profile_private_info (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    full_name text,
    phone text,
    photo_url text,
    location text,
    municipality text,
    contact_email text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT profile_private_info_pkey PRIMARY KEY (id),
    CONSTRAINT profile_private_info_user_id_key UNIQUE (user_id),
    CONSTRAINT profile_private_info_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_profile_private_info_user ON public.profile_private_info USING btree (user_id);

CREATE TABLE profile_social (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    platform text NOT NULL,
    url text NOT NULL,
    username text,
    icon_key text NOT NULL,
    display_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT profile_social_pkey PRIMARY KEY (id),
    CONSTRAINT profile_social_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_profile_social_user ON public.profile_social USING btree (user_id);

CREATE TABLE profile_strength (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    title text NOT NULL,
    description text,
    icon_key text DEFAULT 'star'::text NOT NULL,
    display_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT profile_strength_pkey PRIMARY KEY (id),
    CONSTRAINT profile_strength_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_profile_strength_user ON public.profile_strength USING btree (user_id);

CREATE TABLE profile_languages (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    language character varying(100) NOT NULL,
    proficiency character varying(30) NOT NULL,
    display_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT profile_languages_pkey PRIMARY KEY (id),
    CONSTRAINT profile_languages_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_profile_languages_user_id ON public.profile_languages USING btree (user_id);

CREATE TABLE profile_embeddings (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    embedding vector(1536) NOT NULL,
    model character varying(100) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT pk_profile_embeddings PRIMARY KEY (id),
    CONSTRAINT profile_embeddings_user_id_key UNIQUE (user_id),
    CONSTRAINT fk_profile_embeddings_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
