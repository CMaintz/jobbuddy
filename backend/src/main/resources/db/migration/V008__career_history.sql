-- Career history.
--
-- The factual record a CV is assembled from. Everything a generated document claims has to be
-- traceable to a row here.

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
