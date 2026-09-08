-- Stored CVs.
--
-- Uploaded or generated CV versions an application can be tied back to.

CREATE TABLE cv_versions (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    name character varying(255) NOT NULL,
    content text NOT NULL,
    format character varying(50) DEFAULT 'MARKDOWN'::character varying,
    file_url character varying(1000),
    is_primary boolean DEFAULT false,
    version_number integer DEFAULT 1 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT cv_versions_pkey PRIMARY KEY (id),
    CONSTRAINT cv_versions_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
