-- User HTML templates and the /export-pdf endpoint that rendered them had no caller left. The
-- structured document templates are the only template system, so the table and its seed rows go.
DROP TABLE IF EXISTS pdf_templates;
