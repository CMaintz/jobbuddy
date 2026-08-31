-- Interview preparation.
--
-- The prepared question set, and the candidate's STAR+R stories that answers are drawn from so a
-- mock interview cannot invent experience.

CREATE TABLE interview_questions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    job_id uuid NOT NULL,
    user_id uuid NOT NULL,
    question text NOT NULL,
    category character varying(50),
    star_answer text,
    practiced boolean DEFAULT false NOT NULL,
    display_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT interview_questions_pkey PRIMARY KEY (id),
    CONSTRAINT fk_interview_questions_job FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE,
    CONSTRAINT fk_interview_questions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_interview_questions_job_user ON public.interview_questions USING btree (job_id, user_id);

CREATE TABLE interview_story (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    title text NOT NULL,
    situation text,
    task text,
    action text,
    result text,
    reflection text,
    tags text[] DEFAULT '{}'::text[] NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT interview_story_pkey PRIMARY KEY (id),
    CONSTRAINT interview_story_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_interview_story_user ON public.interview_story USING btree (user_id);
