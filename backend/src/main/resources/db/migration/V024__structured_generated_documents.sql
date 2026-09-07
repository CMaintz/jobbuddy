ALTER TABLE generated_documents
    ADD COLUMN structured_content TEXT,
    ADD COLUMN template_id VARCHAR(100),
    ADD COLUMN export_mode VARCHAR(50);

CREATE INDEX idx_generated_documents_user_type_created
    ON generated_documents(user_id, document_type, created_at DESC);
