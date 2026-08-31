-- Unsolicited outreach.
--
-- Around half of Danish vacancies are never advertised. This tracks who was approached, through
-- which channel and when to follow up, plus the LinkedIn search plan.

CREATE TABLE outreach_contact (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    company_id uuid,
    company_name text NOT NULL,
    status text DEFAULT 'SAVED'::text NOT NULL,
    channel text,
    contact_name text,
    contacted_at timestamp with time zone,
    follow_up_due date,
    notes text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT outreach_contact_pkey PRIMARY KEY (id),
    CONSTRAINT outreach_contact_company_id_fkey FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE SET NULL,
    CONSTRAINT outreach_contact_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX idx_outreach_contact_user_company ON public.outreach_contact USING btree (user_id, company_id) WHERE (company_id IS NOT NULL);
CREATE INDEX idx_outreach_contact_user_followup ON public.outreach_contact USING btree (user_id, follow_up_due) WHERE (follow_up_due IS NOT NULL);

CREATE TABLE linkedin_query_plan (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    keywords text[] DEFAULT '{}'::text[] NOT NULL,
    breadth character varying(20),
    generated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT linkedin_query_plan_pkey PRIMARY KEY (id),
    CONSTRAINT linkedin_query_plan_user_id_key UNIQUE (user_id),
    CONSTRAINT linkedin_query_plan_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_linkedin_query_plan_user ON public.linkedin_query_plan USING btree (user_id);
