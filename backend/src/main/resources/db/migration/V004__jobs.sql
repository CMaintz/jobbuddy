-- Job postings.
--
-- Postings and their embeddings. required_skills / preferred_skills carry the requirement tier a
-- Danish posting states in words ("du skal" versus "det er en fordel"); the matcher weights them
-- differently, and jobs.skills stays the flat union for readers that do not care.

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
