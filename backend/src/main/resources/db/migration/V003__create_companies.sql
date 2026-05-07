CREATE TABLE companies (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(500) NOT NULL,
    slug VARCHAR(500) UNIQUE,
    website VARCHAR(500),
    linkedin_url VARCHAR(500),
    description TEXT,
    logo_url VARCHAR(500),
    size_range VARCHAR(100),
    industry VARCHAR(255),
    country VARCHAR(100),
    is_consulting BOOLEAN DEFAULT FALSE,
    is_recruiting_agency BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE company_metadata (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    key VARCHAR(255) NOT NULL,
    value TEXT,
    source VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
