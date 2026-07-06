-- Prevent duplicate applications: one application per user per job.
ALTER TABLE applications
    ADD CONSTRAINT uq_applications_user_job UNIQUE (user_id, job_id);
