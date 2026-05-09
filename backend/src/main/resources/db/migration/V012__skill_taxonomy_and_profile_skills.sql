CREATE TABLE skill_taxonomy (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(200) NOT NULL UNIQUE,
    normalized_name VARCHAR(200) NOT NULL,
    parent_id UUID REFERENCES skill_taxonomy(id),
    category VARCHAR(100),
    aliases text[],
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_skill_taxonomy_normalized ON skill_taxonomy(normalized_name);
CREATE INDEX idx_skill_taxonomy_category ON skill_taxonomy(category);

-- Seed common skills
INSERT INTO skill_taxonomy (name, normalized_name, category) VALUES
  ('Java', 'java', 'Language'),
  ('Python', 'python', 'Language'),
  ('JavaScript', 'javascript', 'Language'),
  ('TypeScript', 'typescript', 'Language'),
  ('Go', 'go', 'Language'),
  ('Rust', 'rust', 'Language'),
  ('C#', 'c#', 'Language'),
  ('C++', 'c++', 'Language'),
  ('PHP', 'php', 'Language'),
  ('Ruby', 'ruby', 'Language'),
  ('Swift', 'swift', 'Language'),
  ('Kotlin', 'kotlin', 'Language'),
  ('Scala', 'scala', 'Language'),
  ('Spring Boot', 'spring boot', 'Framework'),
  ('Spring', 'spring', 'Framework'),
  ('React', 'react', 'Framework'),
  ('Angular', 'angular', 'Framework'),
  ('Vue.js', 'vue.js', 'Framework'),
  ('Django', 'django', 'Framework'),
  ('FastAPI', 'fastapi', 'Framework'),
  ('Node.js', 'node.js', 'Framework'),
  ('Express', 'express', 'Framework'),
  ('.NET', '.net', 'Framework'),
  ('Laravel', 'laravel', 'Framework'),
  ('AWS', 'aws', 'Cloud'),
  ('Azure', 'azure', 'Cloud'),
  ('GCP', 'gcp', 'Cloud'),
  ('Docker', 'docker', 'DevOps'),
  ('Kubernetes', 'kubernetes', 'DevOps'),
  ('Terraform', 'terraform', 'DevOps'),
  ('PostgreSQL', 'postgresql', 'Database'),
  ('MySQL', 'mysql', 'Database'),
  ('MongoDB', 'mongodb', 'Database'),
  ('Redis', 'redis', 'Database'),
  ('Elasticsearch', 'elasticsearch', 'Database'),
  ('GraphQL', 'graphql', 'API'),
  ('REST', 'rest', 'API'),
  ('Git', 'git', 'Tool'),
  ('Linux', 'linux', 'Tool'),
  ('Machine Learning', 'machine learning', 'AI/ML'),
  ('Deep Learning', 'deep learning', 'AI/ML'),
  ('LLM', 'llm', 'AI/ML'),
  ('Agile', 'agile', 'Methodology'),
  ('Scrum', 'scrum', 'Methodology'),
  ('Microservices', 'microservices', 'Architecture'),
  ('Event-Driven Architecture', 'event-driven architecture', 'Architecture'),
  ('CI/CD', 'ci/cd', 'DevOps'),
  ('TDD', 'tdd', 'Methodology');

-- Per-skill proficiency metadata for user profiles
CREATE TABLE profile_skills (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    skill_name VARCHAR(200) NOT NULL,
    taxonomy_id UUID REFERENCES skill_taxonomy(id),
    proficiency_level VARCHAR(50) DEFAULT 'INTERMEDIATE',
    years_experience INT,
    used_in_production BOOLEAN DEFAULT FALSE,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT unique_user_skill UNIQUE (user_id, skill_name)
);

CREATE INDEX idx_profile_skills_user_id ON profile_skills(user_id);
