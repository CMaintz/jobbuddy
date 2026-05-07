CREATE TABLE jobs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    source VARCHAR(100) NOT NULL,
    source_job_id VARCHAR(500),
    url VARCHAR(2000) NOT NULL,
    title VARCHAR(500) NOT NULL,
    company_id UUID REFERENCES companies(id),
    company_name VARCHAR(500),
    description_raw TEXT,
    description_clean TEXT,
    employment_type VARCHAR(100),
    seniority VARCHAR(100),
    remote_type VARCHAR(100),
    location VARCHAR(500),
    municipality VARCHAR(255),
    region VARCHAR(255),
    country VARCHAR(100) DEFAULT 'DK',
    salary_min INT,
    salary_max INT,
    currency VARCHAR(10) DEFAULT 'DKK',
    technologies TEXT[],
    skills TEXT[],
    languages TEXT[],
    posted_at TIMESTAMPTZ,
    scraped_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ai_summary TEXT,
    ai_tags TEXT[],
    ai_seniority_estimate VARCHAR(100),
    duplicate_group_id UUID,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT unique_source_job UNIQUE (source, source_job_id)
);

CREATE INDEX idx_jobs_source ON jobs(source);
CREATE INDEX idx_jobs_company_id ON jobs(company_id);
CREATE INDEX idx_jobs_posted_at ON jobs(posted_at DESC);
CREATE INDEX idx_jobs_is_active ON jobs(is_active);
CREATE INDEX idx_jobs_technologies ON jobs USING GIN(technologies);
CREATE INDEX idx_jobs_skills ON jobs USING GIN(skills);
CREATE INDEX idx_jobs_duplicate_group ON jobs(duplicate_group_id);

CREATE TABLE job_sources (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    source_name VARCHAR(100) NOT NULL,
    source_url VARCHAR(2000),
    scraped_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE job_embeddings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    embedding vector(1536) NOT NULL,
    model VARCHAR(255) NOT NULL DEFAULT 'text-embedding-3-small',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT unique_job_embedding UNIQUE (job_id, model)
);

CREATE INDEX idx_job_embeddings_vector ON job_embeddings
    USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

CREATE TABLE job_tags (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    tag VARCHAR(255) NOT NULL,
    source VARCHAR(100) DEFAULT 'AI',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
