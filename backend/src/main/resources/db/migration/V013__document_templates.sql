-- Rendering templates.
--
-- The layout shells: how a structured document becomes a page.

CREATE TABLE structured_document_templates (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    template_id character varying(100) NOT NULL,
    family_id character varying(80) NOT NULL,
    family_name character varying(120) NOT NULL,
    name character varying(120) NOT NULL,
    description text,
    document_type character varying(40) NOT NULL,
    layout_type character varying(40) NOT NULL,
    export_mode character varying(20) DEFAULT 'DESIGNED'::character varying NOT NULL,
    supports_profile_image boolean DEFAULT false NOT NULL,
    ats_safe boolean DEFAULT false NOT NULL,
    is_system boolean DEFAULT true NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    display_order integer DEFAULT 0 NOT NULL,
    default_primary_color character varying(7) DEFAULT '#18324a'::character varying NOT NULL,
    default_accent_color character varying(7) DEFAULT '#cbd8e3'::character varying NOT NULL,
    default_font_family character varying(50) DEFAULT 'Arial'::character varying NOT NULL,
    default_font_scale character varying(20) DEFAULT 'normal'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT structured_document_templates_pkey PRIMARY KEY (id),
    CONSTRAINT uq_structured_document_templates_type UNIQUE (template_id, document_type)
);
CREATE INDEX idx_structured_document_templates_active ON public.structured_document_templates USING btree (is_active, display_order);
CREATE INDEX idx_structured_document_templates_family ON public.structured_document_templates USING btree (family_id);

CREATE TABLE pdf_templates (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id uuid,
    name character varying(200) NOT NULL,
    description text,
    document_type character varying(100) DEFAULT 'COVER_LETTER'::character varying NOT NULL,
    html_template text NOT NULL,
    css_styles text,
    is_system boolean DEFAULT false NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT pdf_templates_pkey PRIMARY KEY (id),
    CONSTRAINT pdf_templates_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_pdf_templates_system ON public.pdf_templates USING btree (is_system);
CREATE INDEX idx_pdf_templates_user_id ON public.pdf_templates USING btree (user_id);
