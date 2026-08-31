-- Metrics and usage.
--
-- Outcome rates and AI spend.

CREATE TABLE application_metrics (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    period_start date NOT NULL,
    period_end date NOT NULL,
    total_applications integer DEFAULT 0,
    total_saved integer DEFAULT 0,
    total_ignored integer DEFAULT 0,
    response_rate numeric(5,2),
    interview_rate numeric(5,2),
    offer_rate numeric(5,2),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT application_metrics_pkey PRIMARY KEY (id),
    CONSTRAINT application_metrics_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE response_metrics (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    job_id uuid,
    application_id uuid,
    event_type character varying(100) NOT NULL,
    event_at timestamp with time zone DEFAULT now() NOT NULL,
    metadata jsonb,
    CONSTRAINT response_metrics_pkey PRIMARY KEY (id),
    CONSTRAINT response_metrics_application_id_fkey FOREIGN KEY (application_id) REFERENCES applications(id) ON DELETE SET NULL,
    CONSTRAINT response_metrics_job_id_fkey FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE SET NULL,
    CONSTRAINT response_metrics_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE ai_usage_log (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    model character varying(100) NOT NULL,
    tokens_in integer DEFAULT 0 NOT NULL,
    tokens_out integer DEFAULT 0 NOT NULL,
    operation character varying(50) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT pk_ai_usage_log PRIMARY KEY (id),
    CONSTRAINT fk_ai_usage_log_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_ai_usage_log_user_date ON public.ai_usage_log USING btree (user_id, created_at);
