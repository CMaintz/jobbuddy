package com.autoapplicant.domain.document.structured;

import com.autoapplicant.domain.document.DocumentType;

import java.util.List;
import java.util.UUID;

/**
 * A generated document as the editor, renderer and export see it.
 *
 * @param letterReview for a gated letter, the needs it was written from and the gate's verdict;
 *     null for every other document
 */
public record StructuredDocument(
        UUID generatedDocumentId,
        DocumentType documentType,
        String exportMode,
        String templateId,
        DocumentIdentity identity,
        DocumentRenderOptions options,
        List<StructuredDocumentSection> sections,
        String bodyContent,
        AtsReport atsReport,
        LetterReview letterReview
) {

    public StructuredDocument withLetterReview(LetterReview review) {
        return new StructuredDocument(generatedDocumentId, documentType, exportMode, templateId, identity,
                options, sections, bodyContent, atsReport, review);
    }
}
