-- V038: Add job_category column to jobs table for industry classification.
ALTER TABLE jobs ADD COLUMN job_category VARCHAR(50);
CREATE INDEX idx_jobs_job_category ON jobs(job_category);
