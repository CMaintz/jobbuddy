-- Preferences and direction.
--
-- What the user wants (preferences drive both hard filters and match scoring), how they write, and
-- where they are heading.

CREATE TABLE preferences (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    preferred_locations text[],
    preferred_municipalities text[],
    positive_signals text[],
    negative_signals text[],
    excluded_companies text[],
    notification_enabled boolean DEFAULT true,
    notification_frequency character varying(50) DEFAULT 'DAILY'::character varying,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    preferred_remote_types text[],
    preferred_employment_types text[],
    preferred_seniority text[],
    salary_min integer,
    salary_max integer,
    max_commute_km integer,
    preferred_industries text[],
    weekly_application_goal integer,
    CONSTRAINT preferences_pkey PRIMARY KEY (id),
    CONSTRAINT unique_user_preferences UNIQUE (user_id),
    CONSTRAINT preferences_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE writing_profiles (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    tone text,
    vocabulary_notes text,
    phrasing_patterns text[],
    example_excerpts text[],
    last_analyzed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    style_dos text[],
    style_donts text[],
    structure_notes text,
    CONSTRAINT writing_profiles_pkey PRIMARY KEY (id),
    CONSTRAINT unique_user_writing_profile UNIQUE (user_id),
    CONSTRAINT writing_profiles_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE career_target (
    user_id uuid NOT NULL,
    target_archetypes text[] DEFAULT '{}'::text[] NOT NULL,
    north_star text,
    narrative text,
    culture_requirements text[] DEFAULT '{}'::text[] NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    career_stage text,
    notice_period text,
    earliest_start_date date,
    CONSTRAINT career_target_pkey PRIMARY KEY (user_id),
    CONSTRAINT career_target_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
