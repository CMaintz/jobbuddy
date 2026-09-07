CREATE TABLE structured_document_templates (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    template_id VARCHAR(100) NOT NULL,
    family_id VARCHAR(80) NOT NULL,
    family_name VARCHAR(120) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description TEXT,
    document_type VARCHAR(40) NOT NULL,
    layout_type VARCHAR(40) NOT NULL,
    export_mode VARCHAR(20) NOT NULL DEFAULT 'DESIGNED',
    supports_profile_image BOOLEAN NOT NULL DEFAULT FALSE,
    ats_safe BOOLEAN NOT NULL DEFAULT FALSE,
    is_system BOOLEAN NOT NULL DEFAULT TRUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INT NOT NULL DEFAULT 0,
    default_primary_color VARCHAR(7) NOT NULL DEFAULT '#18324a',
    default_accent_color VARCHAR(7) NOT NULL DEFAULT '#cbd8e3',
    default_font_family VARCHAR(50) NOT NULL DEFAULT 'Arial',
    default_font_scale VARCHAR(20) NOT NULL DEFAULT 'normal',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_structured_document_templates_type UNIQUE (template_id, document_type)
);

CREATE INDEX idx_structured_document_templates_active
    ON structured_document_templates(is_active, display_order);

CREATE INDEX idx_structured_document_templates_family
    ON structured_document_templates(family_id);

INSERT INTO structured_document_templates (
    template_id, family_id, family_name, name, description, document_type, layout_type,
    export_mode, supports_profile_image, ats_safe, display_order,
    default_primary_color, default_accent_color, default_font_family, default_font_scale
) VALUES
('cv-ats-classic', 'classic-ats', 'Classic ATS', 'ATS Classic CV',
 'Single-column CV layout tuned for automated parsing and corporate portals.',
 'CV', 'single-column', 'ATS', FALSE, TRUE, 10, '#111111', '#d1d5db', 'Arial', 'normal'),
('application-ats', 'classic-ats', 'Classic ATS', 'ATS Plain Cover Letter',
 'Plain cover letter/application layout aligned with the ATS Classic CV.',
 'COVER_LETTER', 'single-column', 'ATS', FALSE, TRUE, 11, '#111111', '#d1d5db', 'Arial', 'normal'),
('application-ats', 'classic-ats', 'Classic ATS', 'ATS Plain Application',
 'Plain application text layout aligned with the ATS Classic CV.',
 'APPLICATION_TEXT', 'single-column', 'ATS', FALSE, TRUE, 12, '#111111', '#d1d5db', 'Arial', 'normal'),

('cv-modern-professional', 'modern-professional', 'Modern Professional', 'Modern Professional CV',
 'Balanced modern CV with a strong header, restrained colour, and broad role fit.',
 'CV', 'two-column', 'DESIGNED', TRUE, FALSE, 20, '#18324a', '#cbd8e3', 'Inter', 'normal'),
('application-modern', 'modern-professional', 'Modern Professional', 'Modern Professional Cover Letter',
 'Matching cover letter/application layout for the Modern Professional CV.',
 'COVER_LETTER', 'single-column', 'DESIGNED', TRUE, FALSE, 21, '#18324a', '#cbd8e3', 'Inter', 'normal'),
('application-modern', 'modern-professional', 'Modern Professional', 'Modern Professional Application',
 'Matching application text layout for the Modern Professional CV.',
 'APPLICATION_TEXT', 'single-column', 'DESIGNED', TRUE, FALSE, 22, '#18324a', '#cbd8e3', 'Inter', 'normal'),

('cv-compact-tech', 'compact-tech', 'Compact Tech', 'Compact Tech CV',
 'Dense technical CV layout for skills-heavy software and platform roles.',
 'CV', 'two-column', 'DESIGNED', TRUE, FALSE, 30, '#0f3d3e', '#b7d8d6', 'Inter', 'small'),
('application-compact-tech', 'compact-tech', 'Compact Tech', 'Compact Tech Cover Letter',
 'Concise application layout visually paired with the Compact Tech CV.',
 'COVER_LETTER', 'single-column', 'DESIGNED', TRUE, FALSE, 31, '#0f3d3e', '#b7d8d6', 'Inter', 'small'),
('application-compact-tech', 'compact-tech', 'Compact Tech', 'Compact Tech Application',
 'Concise application text layout visually paired with the Compact Tech CV.',
 'APPLICATION_TEXT', 'single-column', 'DESIGNED', TRUE, FALSE, 32, '#0f3d3e', '#b7d8d6', 'Inter', 'small'),

('cv-executive', 'executive', 'Executive', 'Executive CV',
 'Formal senior-profile CV layout with calmer spacing and classic typography.',
 'CV', 'two-column', 'DESIGNED', TRUE, FALSE, 40, '#2b2338', '#d8cedf', 'Georgia', 'normal'),
('application-executive', 'executive', 'Executive', 'Executive Cover Letter',
 'Formal cover letter/application layout paired with the Executive CV.',
 'COVER_LETTER', 'single-column', 'DESIGNED', TRUE, FALSE, 41, '#2b2338', '#d8cedf', 'Georgia', 'normal'),
('application-executive', 'executive', 'Executive', 'Executive Application',
 'Formal application text layout paired with the Executive CV.',
 'APPLICATION_TEXT', 'single-column', 'DESIGNED', TRUE, FALSE, 42, '#2b2338', '#d8cedf', 'Georgia', 'normal'),

('cv-minimal-scandinavian', 'minimal-scandinavian', 'Minimal Scandinavian', 'Minimal Scandinavian CV',
 'Quiet single-column CV layout with clean spacing and minimal visual noise.',
 'CV', 'single-column', 'DESIGNED', FALSE, FALSE, 50, '#2f3a36', '#d9e4df', 'Calibri', 'normal'),
('application-minimal-scandinavian', 'minimal-scandinavian', 'Minimal Scandinavian', 'Minimal Scandinavian Cover Letter',
 'Minimal application layout paired with the Minimal Scandinavian CV.',
 'COVER_LETTER', 'single-column', 'DESIGNED', FALSE, FALSE, 51, '#2f3a36', '#d9e4df', 'Calibri', 'normal'),
('application-minimal-scandinavian', 'minimal-scandinavian', 'Minimal Scandinavian', 'Minimal Scandinavian Application',
 'Minimal application text layout paired with the Minimal Scandinavian CV.',
 'APPLICATION_TEXT', 'single-column', 'DESIGNED', FALSE, FALSE, 52, '#2f3a36', '#d9e4df', 'Calibri', 'normal');
