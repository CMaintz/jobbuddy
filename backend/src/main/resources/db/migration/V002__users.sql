-- Accounts.
--
-- The account row everything else hangs off.

CREATE TABLE users (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    email character varying(255) NOT NULL,
    google_id character varying(255),
    role character varying(50) DEFAULT 'USER'::character varying NOT NULL,
    email_verified boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    linkedin_id character varying(255),
    firebase_uid character varying(128),
    onboarding_complete boolean DEFAULT false NOT NULL,
    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT users_email_key UNIQUE (email),
    CONSTRAINT users_firebase_uid_key UNIQUE (firebase_uid)
);
CREATE INDEX idx_users_firebase_uid ON public.users USING btree (firebase_uid);
CREATE UNIQUE INDEX idx_users_linkedin_id ON public.users USING btree (linkedin_id) WHERE (linkedin_id IS NOT NULL);
