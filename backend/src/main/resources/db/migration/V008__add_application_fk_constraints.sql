ALTER TABLE applications
    ADD CONSTRAINT fk_applications_cv_version
        FOREIGN KEY (cv_version_id) REFERENCES cv_versions(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_applications_prompt_template
        FOREIGN KEY (prompt_template_id) REFERENCES prompt_templates(id) ON DELETE SET NULL;
