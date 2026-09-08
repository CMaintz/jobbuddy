-- What the user did with a posting.
--
-- Saved, ignored, and the like/dislike signal that nudges future recommendations.

CREATE TABLE saved_jobs (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    job_id uuid NOT NULL,
    saved_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT saved_jobs_pkey PRIMARY KEY (id),
    CONSTRAINT unique_saved_job UNIQUE (user_id, job_id),
    CONSTRAINT saved_jobs_job_id_fkey FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE,
    CONSTRAINT saved_jobs_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE ignored_jobs (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    job_id uuid NOT NULL,
    reason character varying(255),
    ignored_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ignored_jobs_pkey PRIMARY KEY (id),
    CONSTRAINT unique_ignored_job UNIQUE (user_id, job_id),
    CONSTRAINT ignored_jobs_job_id_fkey FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE,
    CONSTRAINT ignored_jobs_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_ignored_jobs_user_id ON public.ignored_jobs USING btree (user_id);

CREATE TABLE recommendation_feedback (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    job_id uuid NOT NULL,
    feedback_type character varying(50) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT recommendation_feedback_pkey PRIMARY KEY (id),
    CONSTRAINT unique_user_job_feedback UNIQUE (user_id, job_id),
    CONSTRAINT recommendation_feedback_job_id_fkey FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE,
    CONSTRAINT recommendation_feedback_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_recommendation_feedback_user_id ON public.recommendation_feedback USING btree (user_id);
