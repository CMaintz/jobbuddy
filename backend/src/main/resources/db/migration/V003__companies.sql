-- Employers.
--
-- Employers, and the facts crawled from their own sites that ground a cover letter's company
-- references in something checkable rather than in the posting's self-description.

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
