-- Applications.
--
-- An application and its history. Danish advice is consistent that the follow-up is what makes an
-- approach work, so reminders are first-class rather than a note to self.

CREATE TABLE applications (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    job_id uuid NOT NULL,
    status character varying(100) DEFAULT 'SAVED'::character varying NOT NULL,
    applied_at timestamp with time zone,
    recruiter_name character varying(255),
    recruiter_email character varying(255),
    cover_letter_text text,
    application_text text,
    recruiter_message text,
    cv_version_id uuid,
    prompt_template_id uuid,
    match_score integer,
    notes text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    recruiter_reply text,
    outcome_feedback text,
    outcome_lessons text,
    CONSTRAINT applications_pkey PRIMARY KEY (id),
    CONSTRAINT uq_applications_user_job UNIQUE (user_id, job_id),
    CONSTRAINT applications_job_id_fkey FOREIGN KEY (job_id) REFERENCES jobs(id),
    CONSTRAINT applications_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_applications_cv_version FOREIGN KEY (cv_version_id) REFERENCES cv_versions(id) ON DELETE SET NULL,
    CONSTRAINT fk_applications_prompt_template FOREIGN KEY (prompt_template_id) REFERENCES prompt_templates(id) ON DELETE SET NULL
);
CREATE INDEX idx_applications_status ON public.applications USING btree (status);
CREATE INDEX idx_applications_user_id ON public.applications USING btree (user_id);

CREATE TABLE application_status_event (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    application_id uuid NOT NULL,
    user_id uuid NOT NULL,
    from_status character varying(40),
    to_status character varying(40) NOT NULL,
    occurred_at timestamp with time zone DEFAULT now() NOT NULL,
    -- What the candidate wrote about this move: the rejection reason, what the recruiter
    -- said, why they sat on it for a week. On a rejection it is the only part worth
    -- reading twice.
    notes text,
    CONSTRAINT application_status_event_pkey PRIMARY KEY (id),
    CONSTRAINT application_status_event_application_id_fkey FOREIGN KEY (application_id) REFERENCES applications(id) ON DELETE CASCADE,
    CONSTRAINT application_status_event_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_status_event_app ON public.application_status_event USING btree (application_id, occurred_at);
CREATE INDEX idx_status_event_user ON public.application_status_event USING btree (user_id, occurred_at);

CREATE TABLE follow_up_reminders (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    application_id uuid NOT NULL,
    user_id uuid NOT NULL,
    note text,
    due_at timestamp with time zone NOT NULL,
    completed boolean DEFAULT false NOT NULL,
    completed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT follow_up_reminders_pkey PRIMARY KEY (id),
    CONSTRAINT fk_follow_up_reminders_application FOREIGN KEY (application_id) REFERENCES applications(id) ON DELETE CASCADE,
    CONSTRAINT fk_follow_up_reminders_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_follow_up_reminders_app ON public.follow_up_reminders USING btree (application_id);
CREATE INDEX idx_follow_up_reminders_user_due ON public.follow_up_reminders USING btree (user_id, due_at) WHERE (completed = false);

CREATE TABLE notes (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    job_id uuid,
    application_id uuid,
    content text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT notes_pkey PRIMARY KEY (id),
    CONSTRAINT notes_application_id_fkey FOREIGN KEY (application_id) REFERENCES applications(id) ON DELETE SET NULL,
    CONSTRAINT notes_job_id_fkey FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE SET NULL,
    CONSTRAINT notes_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
