-- User-authored custom CV sections (e.g. "Publications", "Volunteering"), one row per user.
--
-- Master-profile data with stable ids so AI tailoring can select/rewrite within a section while the
-- assembler validates every item back to source by id — the same honesty discipline as the typed
-- sections. Stored as jsonb: [{id, heading, items:[{id, text}]}].

-- One row per user, so user_id is the natural primary key (no synthetic id).
CREATE TABLE custom_sections (
    user_id uuid NOT NULL,
    sections jsonb DEFAULT '[]'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT custom_sections_pkey PRIMARY KEY (user_id),
    CONSTRAINT custom_sections_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
