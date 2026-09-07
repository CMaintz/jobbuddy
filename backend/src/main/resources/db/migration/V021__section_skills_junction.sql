-- Junction tables linking profile sections to skill taxonomy entries
-- ON DELETE CASCADE means removing a section or taxonomy entry cleans up links automatically

CREATE TABLE work_experience_skills (
    work_experience_id UUID NOT NULL REFERENCES work_experiences(id) ON DELETE CASCADE,
    taxonomy_id        UUID NOT NULL REFERENCES skill_taxonomy(id) ON DELETE CASCADE,
    PRIMARY KEY (work_experience_id, taxonomy_id)
);

CREATE TABLE project_skills (
    project_id  UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    taxonomy_id UUID NOT NULL REFERENCES skill_taxonomy(id) ON DELETE CASCADE,
    PRIMARY KEY (project_id, taxonomy_id)
);

CREATE TABLE education_skills (
    education_id UUID NOT NULL REFERENCES education(id) ON DELETE CASCADE,
    taxonomy_id  UUID NOT NULL REFERENCES skill_taxonomy(id) ON DELETE CASCADE,
    PRIMARY KEY (education_id, taxonomy_id)
);
