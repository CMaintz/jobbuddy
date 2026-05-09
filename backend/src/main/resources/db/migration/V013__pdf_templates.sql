CREATE TABLE pdf_templates (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    document_type VARCHAR(100) NOT NULL DEFAULT 'COVER_LETTER',
    html_template TEXT NOT NULL,
    css_styles TEXT,
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pdf_templates_user_id ON pdf_templates(user_id);
CREATE INDEX idx_pdf_templates_system ON pdf_templates(is_system);

INSERT INTO pdf_templates (name, description, document_type, is_system, html_template, css_styles) VALUES
(
  'Clean Modern',
  'Professional layout with header bar and clean typography',
  'COVER_LETTER',
  TRUE,
  '<!DOCTYPE html><html><head><meta charset="UTF-8"><style>{{CSS}}</style></head><body class="page"><header class="header"><div class="name-block"><h1>{{NAME}}</h1><p class="title">{{HEADLINE}}</p></div><div class="contact-block"><p>{{EMAIL}}</p><p>{{PHONE}}</p><p>{{LOCATION}}</p><p>{{LINKEDIN}}</p></div></header><main class="content"><div class="body-text">{{CONTENT}}</div></main></body></html>',
  'body{font-family:Georgia,serif;font-size:11pt;color:#1a1a1a;margin:0;padding:0}.page{width:210mm;min-height:297mm;padding:20mm;box-sizing:border-box}.header{display:flex;justify-content:space-between;align-items:flex-start;padding-bottom:16px;margin-bottom:20px;border-bottom:2px solid #2563eb}.name-block h1{margin:0;font-size:22pt;color:#1e3a5f}.name-block .title{margin:4px 0 0;color:#2563eb;font-size:11pt}.contact-block{text-align:right;font-size:9pt;color:#444}.contact-block p{margin:2px 0}.content .body-text{line-height:1.75;white-space:pre-wrap}'
),
(
  'ATS Safe',
  'Plain text layout optimised for ATS systems — no columns or graphics',
  'COVER_LETTER',
  TRUE,
  '<!DOCTYPE html><html><head><meta charset="UTF-8"><style>{{CSS}}</style></head><body class="page"><div class="header-text"><strong>{{NAME}}</strong><br>{{EMAIL}} | {{PHONE}} | {{LOCATION}}</div><hr><div class="body-text">{{CONTENT}}</div></body></html>',
  'body{font-family:Arial,sans-serif;font-size:11pt;color:#000;margin:0}.page{width:210mm;min-height:297mm;padding:20mm;box-sizing:border-box}.header-text{margin-bottom:8px;line-height:1.6}hr{margin:12px 0;border:none;border-top:1px solid #000}.body-text{line-height:1.75;white-space:pre-wrap}'
),
(
  'Clean Modern CV',
  'Two-section CV layout with contact sidebar and main content',
  'CV',
  TRUE,
  '<!DOCTYPE html><html><head><meta charset="UTF-8"><style>{{CSS}}</style></head><body class="page"><div class="layout"><aside class="sidebar"><div class="avatar-placeholder"></div><h2>{{NAME}}</h2><p class="title">{{HEADLINE}}</p><div class="contact"><p>{{EMAIL}}</p><p>{{PHONE}}</p><p>{{LOCATION}}</p><p>{{LINKEDIN}}</p></div></aside><main class="main"><div class="body-text">{{CONTENT}}</div></main></div></body></html>',
  'body{font-family:''Helvetica Neue'',sans-serif;font-size:10pt;color:#1a1a1a;margin:0}.page{width:210mm;min-height:297mm;box-sizing:border-box}.layout{display:flex;min-height:297mm}.sidebar{width:65mm;background:#1e3a5f;color:#fff;padding:16mm 10mm;box-sizing:border-box}.sidebar h2{margin:12px 0 4px;font-size:14pt}.sidebar .title{font-size:9pt;color:#93c5fd;margin:0 0 16px}.sidebar .contact{font-size:8pt;line-height:1.8}.sidebar .contact p{margin:2px 0}.avatar-placeholder{width:70px;height:70px;border-radius:50%;background:#93c5fd;margin:0 auto 12px}.main{flex:1;padding:16mm 12mm}.body-text{line-height:1.65;white-space:pre-wrap}'
);
