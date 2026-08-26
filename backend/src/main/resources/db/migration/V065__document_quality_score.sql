-- Quality scores for generated documents, recorded by DocumentQualityEvaluator.
--
-- A separate table rather than columns on generated_documents: a score is a measurement ABOUT a
-- document, not a property of it, and the evaluator's rubric will change — keeping scorings as
-- rows means an older score stays interpretable next to the version that produced it, and a
-- re-scoring never overwrites history.
CREATE TABLE document_quality_score (
    id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id               UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    generated_document_id UUID REFERENCES generated_documents (id) ON DELETE CASCADE,
    document_type         TEXT NOT NULL,
    total                 INT  NOT NULL,
    -- Per-dimension breakdown as JSON, so a rubric change does not need a migration.
    dimensions            TEXT NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_document_quality_score_user_created
    ON document_quality_score (user_id, created_at DESC);
