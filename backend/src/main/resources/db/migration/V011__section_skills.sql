-- Skills tagged to a section.
--
-- Which skills a particular job, degree or project actually evidences — the difference between
-- claiming a skill and being able to point at where it was used.

CREATE TABLE work_experience_skills (
    work_experience_id uuid NOT NULL,
    taxonomy_id uuid NOT NULL,
    CONSTRAINT work_experience_skills_pkey PRIMARY KEY (work_experience_id, taxonomy_id),
    CONSTRAINT work_experience_skills_taxonomy_id_fkey FOREIGN KEY (taxonomy_id) REFERENCES skill_taxonomy(id) ON DELETE CASCADE,
    CONSTRAINT work_experience_skills_work_experience_id_fkey FOREIGN KEY (work_experience_id) REFERENCES work_experiences(id) ON DELETE CASCADE
);

CREATE TABLE education_skills (
    education_id uuid NOT NULL,
    taxonomy_id uuid NOT NULL,
    CONSTRAINT education_skills_pkey PRIMARY KEY (education_id, taxonomy_id),
    CONSTRAINT education_skills_education_id_fkey FOREIGN KEY (education_id) REFERENCES education(id) ON DELETE CASCADE,
    CONSTRAINT education_skills_taxonomy_id_fkey FOREIGN KEY (taxonomy_id) REFERENCES skill_taxonomy(id) ON DELETE CASCADE
);

CREATE TABLE project_skills (
    project_id uuid NOT NULL,
    taxonomy_id uuid NOT NULL,
    CONSTRAINT project_skills_pkey PRIMARY KEY (project_id, taxonomy_id),
    CONSTRAINT project_skills_project_id_fkey FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT project_skills_taxonomy_id_fkey FOREIGN KEY (taxonomy_id) REFERENCES skill_taxonomy(id) ON DELETE CASCADE
);
