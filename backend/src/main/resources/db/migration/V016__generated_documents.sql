-- Generated documents.
--
-- What the AI produced, the score it was graded against, and the builder draft the user edits.

CREATE TABLE generated_documents (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    application_id uuid,
    job_id uuid,
    document_type character varying(100) NOT NULL,
    content text NOT NULL,
    prompt_template_id uuid,
    cv_version_id uuid,
    model_used character varying(255),
    tokens_used integer,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    structured_content text,
    template_id character varying(100),
    export_mode character varying(50),
    CONSTRAINT generated_documents_pkey PRIMARY KEY (id),
    CONSTRAINT generated_documents_application_id_fkey FOREIGN KEY (application_id) REFERENCES applications(id) ON DELETE SET NULL,
    CONSTRAINT generated_documents_cv_version_id_fkey FOREIGN KEY (cv_version_id) REFERENCES cv_versions(id),
    CONSTRAINT generated_documents_job_id_fkey FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE SET NULL,
    CONSTRAINT generated_documents_prompt_template_id_fkey FOREIGN KEY (prompt_template_id) REFERENCES prompt_templates(id),
    CONSTRAINT generated_documents_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_generated_documents_user_type_created ON public.generated_documents USING btree (user_id, document_type, created_at DESC);

CREATE TABLE document_quality_score (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    generated_document_id uuid,
    document_type text NOT NULL,
    total integer NOT NULL,
    dimensions text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT document_quality_score_pkey PRIMARY KEY (id),
    CONSTRAINT document_quality_score_generated_document_id_fkey FOREIGN KEY (generated_document_id) REFERENCES generated_documents(id) ON DELETE CASCADE,
    CONSTRAINT document_quality_score_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_document_quality_score_user_created ON public.document_quality_score USING btree (user_id, created_at DESC);

CREATE TABLE resume_draft (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    name text DEFAULT 'My Resume'::text NOT NULL,
    job_id uuid,
    application_id uuid,
    resume_data jsonb DEFAULT '{}'::jsonb NOT NULL,
    settings jsonb DEFAULT '{}'::jsonb NOT NULL,
    status text DEFAULT 'DRAFT'::text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT resume_draft_pkey PRIMARY KEY (id),
    CONSTRAINT resume_draft_application_id_fkey FOREIGN KEY (application_id) REFERENCES applications(id) ON DELETE SET NULL,
    CONSTRAINT resume_draft_job_id_fkey FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE SET NULL,
    CONSTRAINT resume_draft_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_resume_draft_application ON public.resume_draft USING btree (application_id);
CREATE INDEX idx_resume_draft_job ON public.resume_draft USING btree (job_id);
CREATE INDEX idx_resume_draft_user ON public.resume_draft USING btree (user_id);
