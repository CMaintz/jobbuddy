-- Baseline schema.
--
-- This replaces 74 incremental migrations that created tables and then altered them dozens of
-- times over. The app has never been deployed, so there is no history worth preserving and no
-- installed database to migrate: every table is created here with the columns it actually ends up
-- with, which is both shorter to read and impossible to get out of step with itself.
--
-- Generated from the schema those 74 migrations produced, then verified by applying this file to
-- an empty database and diffing the result against theirs.

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE companies (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    name character varying(500) NOT NULL,
    slug character varying(500),
    website character varying(500),
    linkedin_url character varying(500),
    description text,
    logo_url character varying(500),
    size_range character varying(100),
    industry character varying(255),
    country character varying(100),
    is_consulting boolean DEFAULT false,
    is_recruiting_agency boolean DEFAULT false,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    researched_facts text,
    facts_researched_at timestamp with time zone,
    CONSTRAINT companies_pkey PRIMARY KEY (id),
    CONSTRAINT companies_slug_key UNIQUE (slug)
);

CREATE TABLE crawler_state (
    source character varying(50) NOT NULL,
    page_offset integer DEFAULT 0 NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    last_crawl_started_at timestamp with time zone,
    last_crawl_finished_at timestamp with time zone,
    jobs_found integer DEFAULT 0 NOT NULL,
    jobs_ingested integer DEFAULT 0 NOT NULL,
    last_error text,
    is_running boolean DEFAULT false NOT NULL,
    CONSTRAINT pk_crawler_state PRIMARY KEY (source)
);

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

CREATE TABLE structured_document_templates (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    template_id character varying(100) NOT NULL,
    family_id character varying(80) NOT NULL,
    family_name character varying(120) NOT NULL,
    name character varying(120) NOT NULL,
    description text,
    document_type character varying(40) NOT NULL,
    layout_type character varying(40) NOT NULL,
    export_mode character varying(20) DEFAULT 'DESIGNED'::character varying NOT NULL,
    supports_profile_image boolean DEFAULT false NOT NULL,
    ats_safe boolean DEFAULT false NOT NULL,
    is_system boolean DEFAULT true NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    display_order integer DEFAULT 0 NOT NULL,
    default_primary_color character varying(7) DEFAULT '#18324a'::character varying NOT NULL,
    default_accent_color character varying(7) DEFAULT '#cbd8e3'::character varying NOT NULL,
    default_font_family character varying(50) DEFAULT 'Arial'::character varying NOT NULL,
    default_font_scale character varying(20) DEFAULT 'normal'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT structured_document_templates_pkey PRIMARY KEY (id),
    CONSTRAINT uq_structured_document_templates_type UNIQUE (template_id, document_type)
);
CREATE INDEX idx_structured_document_templates_active ON public.structured_document_templates USING btree (is_active, display_order);
CREATE INDEX idx_structured_document_templates_family ON public.structured_document_templates USING btree (family_id);

CREATE TABLE users (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    email character varying(255) NOT NULL,
    google_id character varying(255),
    role character varying(50) DEFAULT 'USER'::character varying NOT NULL,
    email_verified boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    linkedin_id character varying(255),
    firebase_uid character varying(128),
    onboarding_complete boolean DEFAULT false NOT NULL,
    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT users_email_key UNIQUE (email),
    CONSTRAINT users_firebase_uid_key UNIQUE (firebase_uid)
);
CREATE INDEX idx_users_firebase_uid ON public.users USING btree (firebase_uid);
CREATE UNIQUE INDEX idx_users_linkedin_id ON public.users USING btree (linkedin_id) WHERE (linkedin_id IS NOT NULL);

CREATE TABLE ai_usage_log (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    model character varying(100) NOT NULL,
    tokens_in integer DEFAULT 0 NOT NULL,
    tokens_out integer DEFAULT 0 NOT NULL,
    operation character varying(50) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT pk_ai_usage_log PRIMARY KEY (id),
    CONSTRAINT fk_ai_usage_log_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_ai_usage_log_user_date ON public.ai_usage_log USING btree (user_id, created_at);

CREATE TABLE application_metrics (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    period_start date NOT NULL,
    period_end date NOT NULL,
    total_applications integer DEFAULT 0,
    total_saved integer DEFAULT 0,
    total_ignored integer DEFAULT 0,
    response_rate numeric(5,2),
    interview_rate numeric(5,2),
    offer_rate numeric(5,2),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT application_metrics_pkey PRIMARY KEY (id),
    CONSTRAINT application_metrics_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
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

CREATE TABLE certifications (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    name character varying(255) NOT NULL,
    issuer character varying(255),
    issued_at date,
    expires_at date,
    credential_url character varying(500),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT certifications_pkey PRIMARY KEY (id),
    CONSTRAINT certifications_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_certifications_user_id ON public.certifications USING btree (user_id);

CREATE TABLE company_metadata (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    company_id uuid NOT NULL,
    key character varying(255) NOT NULL,
    value text,
    source character varying(255),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT company_metadata_pkey PRIMARY KEY (id),
    CONSTRAINT company_metadata_company_id_fkey FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
);

CREATE TABLE cv_versions (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    name character varying(255) NOT NULL,
    content text NOT NULL,
    format character varying(50) DEFAULT 'MARKDOWN'::character varying,
    file_url character varying(1000),
    is_primary boolean DEFAULT false,
    version_number integer DEFAULT 1 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT cv_versions_pkey PRIMARY KEY (id),
    CONSTRAINT cv_versions_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE education (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    institution character varying(255) NOT NULL,
    degree character varying(255),
    field_of_study character varying(255),
    start_date date,
    end_date date,
    description text,
    grade character varying(50),
    display_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT education_pkey PRIMARY KEY (id),
    CONSTRAINT education_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_education_user_id ON public.education USING btree (user_id);

CREATE TABLE interview_story (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    title text NOT NULL,
    situation text,
    task text,
    action text,
    result text,
    reflection text,
    tags text[] DEFAULT '{}'::text[] NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT interview_story_pkey PRIMARY KEY (id),
    CONSTRAINT interview_story_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_interview_story_user ON public.interview_story USING btree (user_id);

CREATE TABLE jobs (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    source character varying(100) NOT NULL,
    source_job_id character varying(500),
    url character varying(2000) NOT NULL,
    title character varying(500) NOT NULL,
    company_id uuid,
    company_name character varying(500),
    description_raw text,
    description_clean text,
    employment_type character varying(100),
    seniority character varying(100),
    remote_type character varying(100),
    location character varying(500),
    municipality character varying(255),
    region character varying(255),
    country character varying(100) DEFAULT 'DK'::character varying,
    salary_min integer,
    salary_max integer,
    currency character varying(10) DEFAULT 'DKK'::character varying,
    technologies text[],
    skills text[],
    languages text[],
    posted_at timestamp with time zone,
    scraped_at timestamp with time zone DEFAULT now() NOT NULL,
    ai_summary text,
    ai_tags text[],
    ai_seniority_estimate character varying(100),
    duplicate_group_id uuid,
    is_active boolean DEFAULT true,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    job_category character varying(50),
    short_description text,
    last_seen_at timestamp with time zone,
    last_url_check_at timestamp with time zone,
    url_check_failures integer DEFAULT 0 NOT NULL,
    application_deadline date,
    content_fingerprint bigint,
    contact_name text,
    contact_title text,
    contact_email text,
    contact_phone text,
    required_skills text[] DEFAULT '{}'::text[],
    preferred_skills text[] DEFAULT '{}'::text[],
    CONSTRAINT jobs_pkey PRIMARY KEY (id),
    CONSTRAINT unique_source_job UNIQUE (source, source_job_id),
    CONSTRAINT jobs_company_id_fkey FOREIGN KEY (company_id) REFERENCES companies(id)
);
CREATE INDEX idx_jobs_company_id ON public.jobs USING btree (company_id);
CREATE INDEX idx_jobs_content_fingerprint ON public.jobs USING btree (content_fingerprint) WHERE (content_fingerprint IS NOT NULL);
CREATE INDEX idx_jobs_duplicate_group ON public.jobs USING btree (duplicate_group_id);
CREATE INDEX idx_jobs_is_active ON public.jobs USING btree (is_active);
CREATE INDEX idx_jobs_job_category ON public.jobs USING btree (job_category);
CREATE INDEX idx_jobs_posted_at ON public.jobs USING btree (posted_at DESC);
CREATE INDEX idx_jobs_skills ON public.jobs USING gin (skills);
CREATE INDEX idx_jobs_source ON public.jobs USING btree (source);
CREATE INDEX idx_jobs_technologies ON public.jobs USING gin (technologies);
CREATE INDEX idx_jobs_url ON public.jobs USING btree (url);
CREATE INDEX idx_jobs_url_check ON public.jobs USING btree (last_url_check_at NULLS FIRST) WHERE (is_active = true);

CREATE TABLE linkedin_query_plan (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    keywords text[] DEFAULT '{}'::text[] NOT NULL,
    breadth character varying(20),
    generated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT linkedin_query_plan_pkey PRIMARY KEY (id),
    CONSTRAINT linkedin_query_plan_user_id_key UNIQUE (user_id),
    CONSTRAINT linkedin_query_plan_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_linkedin_query_plan_user ON public.linkedin_query_plan USING btree (user_id);

CREATE TABLE outreach_contact (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    company_id uuid,
    company_name text NOT NULL,
    status text DEFAULT 'SAVED'::text NOT NULL,
    channel text,
    contact_name text,
    contacted_at timestamp with time zone,
    follow_up_due date,
    notes text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT outreach_contact_pkey PRIMARY KEY (id),
    CONSTRAINT outreach_contact_company_id_fkey FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE SET NULL,
    CONSTRAINT outreach_contact_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX idx_outreach_contact_user_company ON public.outreach_contact USING btree (user_id, company_id) WHERE (company_id IS NOT NULL);
CREATE INDEX idx_outreach_contact_user_followup ON public.outreach_contact USING btree (user_id, follow_up_due) WHERE (follow_up_due IS NOT NULL);

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

CREATE TABLE pdf_templates (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid,
    name character varying(200) NOT NULL,
    description text,
    document_type character varying(100) DEFAULT 'COVER_LETTER'::character varying NOT NULL,
    html_template text NOT NULL,
    css_styles text,
    is_system boolean DEFAULT false NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT pdf_templates_pkey PRIMARY KEY (id),
    CONSTRAINT pdf_templates_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_pdf_templates_system ON public.pdf_templates USING btree (is_system);
CREATE INDEX idx_pdf_templates_user_id ON public.pdf_templates USING btree (user_id);

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

CREATE TABLE projects (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    technologies text[],
    github_url character varying(500),
    live_url character varying(500),
    architecture_notes text,
    measurable_outcomes text,
    business_impact text,
    start_date date,
    end_date date,
    is_featured boolean DEFAULT false NOT NULL,
    display_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT projects_pkey PRIMARY KEY (id),
    CONSTRAINT projects_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_projects_user_id ON public.projects USING btree (user_id);

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

CREATE TABLE retracted_claim (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    claim text NOT NULL,
    reason text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT retracted_claim_pkey PRIMARY KEY (id),
    CONSTRAINT retracted_claim_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_retracted_claim_user ON public.retracted_claim USING btree (user_id);

CREATE TABLE skill_candidate_dismissal (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    normalized_name text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT skill_candidate_dismissal_pkey PRIMARY KEY (id),
    CONSTRAINT skill_candidate_dismissal_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX idx_skill_candidate_dismissal_user_name ON public.skill_candidate_dismissal USING btree (user_id, normalized_name);

CREATE TABLE work_experiences (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    company_name character varying(255) NOT NULL,
    title character varying(255) NOT NULL,
    location character varying(255),
    description text,
    start_date date NOT NULL,
    end_date date,
    is_current boolean DEFAULT false NOT NULL,
    technologies text[],
    achievements text[],
    display_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT work_experiences_pkey PRIMARY KEY (id),
    CONSTRAINT work_experiences_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_work_experiences_user_id ON public.work_experiences USING btree (user_id);

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

CREATE TABLE applications (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    job_id uuid NOT NULL,
    status character varying(100) DEFAULT 'SAVED'::character varying NOT NULL,
    applied_at timestamp with time zone,
    recruiter_name character varying(255),
    recruiter_email character varying(255),
    cover_letter_text text,
    application_text text,
    recruiter_message text,
    cv_version_id uuid,
    prompt_template_id uuid,
    match_score integer,
    notes text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    recruiter_reply text,
    outcome_feedback text,
    outcome_lessons text,
    CONSTRAINT applications_pkey PRIMARY KEY (id),
    CONSTRAINT uq_applications_user_job UNIQUE (user_id, job_id),
    CONSTRAINT applications_job_id_fkey FOREIGN KEY (job_id) REFERENCES jobs(id),
    CONSTRAINT applications_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_applications_cv_version FOREIGN KEY (cv_version_id) REFERENCES cv_versions(id) ON DELETE SET NULL,
    CONSTRAINT fk_applications_prompt_template FOREIGN KEY (prompt_template_id) REFERENCES prompt_templates(id) ON DELETE SET NULL
);
CREATE INDEX idx_applications_status ON public.applications USING btree (status);
CREATE INDEX idx_applications_user_id ON public.applications USING btree (user_id);

CREATE TABLE education_skills (
    education_id uuid NOT NULL,
    taxonomy_id uuid NOT NULL,
    CONSTRAINT education_skills_pkey PRIMARY KEY (education_id, taxonomy_id),
    CONSTRAINT education_skills_education_id_fkey FOREIGN KEY (education_id) REFERENCES education(id) ON DELETE CASCADE,
    CONSTRAINT education_skills_taxonomy_id_fkey FOREIGN KEY (taxonomy_id) REFERENCES skill_taxonomy(id) ON DELETE CASCADE
);

CREATE TABLE ignored_jobs (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    job_id uuid NOT NULL,
    reason character varying(255),
    ignored_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ignored_jobs_pkey PRIMARY KEY (id),
    CONSTRAINT unique_ignored_job UNIQUE (user_id, job_id),
    CONSTRAINT ignored_jobs_job_id_fkey FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE,
    CONSTRAINT ignored_jobs_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_ignored_jobs_user_id ON public.ignored_jobs USING btree (user_id);

CREATE TABLE interview_questions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    job_id uuid NOT NULL,
    user_id uuid NOT NULL,
    question text NOT NULL,
    category character varying(50),
    star_answer text,
    practiced boolean DEFAULT false NOT NULL,
    display_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT interview_questions_pkey PRIMARY KEY (id),
    CONSTRAINT fk_interview_questions_job FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE,
    CONSTRAINT fk_interview_questions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_interview_questions_job_user ON public.interview_questions USING btree (job_id, user_id);

CREATE TABLE job_embeddings (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    job_id uuid NOT NULL,
    model character varying(255) DEFAULT 'text-embedding-3-small'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    embedding vector(1536) NOT NULL,
    CONSTRAINT job_embeddings_pkey PRIMARY KEY (id),
    CONSTRAINT unique_job_embedding UNIQUE (job_id, model),
    CONSTRAINT job_embeddings_job_id_fkey FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE
);

CREATE TABLE project_skills (
    project_id uuid NOT NULL,
    taxonomy_id uuid NOT NULL,
    CONSTRAINT project_skills_pkey PRIMARY KEY (project_id, taxonomy_id),
    CONSTRAINT project_skills_project_id_fkey FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT project_skills_taxonomy_id_fkey FOREIGN KEY (taxonomy_id) REFERENCES skill_taxonomy(id) ON DELETE CASCADE
);

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

CREATE TABLE recommendation_feedback (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    job_id uuid NOT NULL,
    feedback_type character varying(50) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT recommendation_feedback_pkey PRIMARY KEY (id),
    CONSTRAINT unique_user_job_feedback UNIQUE (user_id, job_id),
    CONSTRAINT recommendation_feedback_job_id_fkey FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE,
    CONSTRAINT recommendation_feedback_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_recommendation_feedback_user_id ON public.recommendation_feedback USING btree (user_id);

CREATE TABLE saved_jobs (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    job_id uuid NOT NULL,
    saved_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT saved_jobs_pkey PRIMARY KEY (id),
    CONSTRAINT unique_saved_job UNIQUE (user_id, job_id),
    CONSTRAINT saved_jobs_job_id_fkey FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE,
    CONSTRAINT saved_jobs_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

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

CREATE TABLE work_experience_skills (
    work_experience_id uuid NOT NULL,
    taxonomy_id uuid NOT NULL,
    CONSTRAINT work_experience_skills_pkey PRIMARY KEY (work_experience_id, taxonomy_id),
    CONSTRAINT work_experience_skills_taxonomy_id_fkey FOREIGN KEY (taxonomy_id) REFERENCES skill_taxonomy(id) ON DELETE CASCADE,
    CONSTRAINT work_experience_skills_work_experience_id_fkey FOREIGN KEY (work_experience_id) REFERENCES work_experiences(id) ON DELETE CASCADE
);

CREATE TABLE application_status_event (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    application_id uuid NOT NULL,
    user_id uuid NOT NULL,
    from_status character varying(40),
    to_status character varying(40) NOT NULL,
    occurred_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT application_status_event_pkey PRIMARY KEY (id),
    CONSTRAINT application_status_event_application_id_fkey FOREIGN KEY (application_id) REFERENCES applications(id) ON DELETE CASCADE,
    CONSTRAINT application_status_event_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_status_event_app ON public.application_status_event USING btree (application_id, occurred_at);
CREATE INDEX idx_status_event_user ON public.application_status_event USING btree (user_id, occurred_at);

CREATE TABLE follow_up_reminders (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    application_id uuid NOT NULL,
    user_id uuid NOT NULL,
    note text,
    due_at timestamp with time zone NOT NULL,
    completed boolean DEFAULT false NOT NULL,
    completed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT follow_up_reminders_pkey PRIMARY KEY (id),
    CONSTRAINT fk_follow_up_reminders_application FOREIGN KEY (application_id) REFERENCES applications(id) ON DELETE CASCADE,
    CONSTRAINT fk_follow_up_reminders_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_follow_up_reminders_app ON public.follow_up_reminders USING btree (application_id);
CREATE INDEX idx_follow_up_reminders_user_due ON public.follow_up_reminders USING btree (user_id, due_at) WHERE (completed = false);

CREATE TABLE generated_documents (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    application_id uuid,
    job_id uuid,
    document_type character varying(100) NOT NULL,
    content text NOT NULL,
    prompt_template_id uuid,
    cv_version_id uuid,
    model_used character varying(255),
    tokens_used integer,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    structured_content text,
    template_id character varying(100),
    export_mode character varying(50),
    CONSTRAINT generated_documents_pkey PRIMARY KEY (id),
    CONSTRAINT generated_documents_application_id_fkey FOREIGN KEY (application_id) REFERENCES applications(id) ON DELETE SET NULL,
    CONSTRAINT generated_documents_cv_version_id_fkey FOREIGN KEY (cv_version_id) REFERENCES cv_versions(id),
    CONSTRAINT generated_documents_job_id_fkey FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE SET NULL,
    CONSTRAINT generated_documents_prompt_template_id_fkey FOREIGN KEY (prompt_template_id) REFERENCES prompt_templates(id),
    CONSTRAINT generated_documents_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_generated_documents_user_type_created ON public.generated_documents USING btree (user_id, document_type, created_at DESC);

CREATE TABLE notes (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    job_id uuid,
    application_id uuid,
    content text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT notes_pkey PRIMARY KEY (id),
    CONSTRAINT notes_application_id_fkey FOREIGN KEY (application_id) REFERENCES applications(id) ON DELETE SET NULL,
    CONSTRAINT notes_job_id_fkey FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE SET NULL,
    CONSTRAINT notes_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE response_metrics (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    job_id uuid,
    application_id uuid,
    event_type character varying(100) NOT NULL,
    event_at timestamp with time zone DEFAULT now() NOT NULL,
    metadata jsonb,
    CONSTRAINT response_metrics_pkey PRIMARY KEY (id),
    CONSTRAINT response_metrics_application_id_fkey FOREIGN KEY (application_id) REFERENCES applications(id) ON DELETE SET NULL,
    CONSTRAINT response_metrics_job_id_fkey FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE SET NULL,
    CONSTRAINT response_metrics_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE resume_draft (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    name text DEFAULT 'My Resume'::text NOT NULL,
    job_id uuid,
    application_id uuid,
    resume_data jsonb DEFAULT '{}'::jsonb NOT NULL,
    settings jsonb DEFAULT '{}'::jsonb NOT NULL,
    status text DEFAULT 'DRAFT'::text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT resume_draft_pkey PRIMARY KEY (id),
    CONSTRAINT resume_draft_application_id_fkey FOREIGN KEY (application_id) REFERENCES applications(id) ON DELETE SET NULL,
    CONSTRAINT resume_draft_job_id_fkey FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE SET NULL,
    CONSTRAINT resume_draft_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_resume_draft_application ON public.resume_draft USING btree (application_id);
CREATE INDEX idx_resume_draft_job ON public.resume_draft USING btree (job_id);
CREATE INDEX idx_resume_draft_user ON public.resume_draft USING btree (user_id);

CREATE TABLE document_quality_score (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    generated_document_id uuid,
    document_type text NOT NULL,
    total integer NOT NULL,
    dimensions text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT document_quality_score_pkey PRIMARY KEY (id),
    CONSTRAINT document_quality_score_generated_document_id_fkey FOREIGN KEY (generated_document_id) REFERENCES generated_documents(id) ON DELETE CASCADE,
    CONSTRAINT document_quality_score_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_document_quality_score_user_created ON public.document_quality_score USING btree (user_id, created_at DESC);
