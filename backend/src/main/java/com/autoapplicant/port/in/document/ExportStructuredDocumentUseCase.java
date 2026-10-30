package com.autoapplicant.port.in.document;

import com.autoapplicant.domain.document.ExportedPdf;
import com.autoapplicant.domain.document.structured.StructuredDocument;

public interface ExportStructuredDocumentUseCase {

    /**
     * The document as a PDF with its page count against its budget (a CV 2 pages, anything else
     * 1). Going over is logged, or refused with {@link IllegalStateException} when the page
     * budget is strict.
     */
    ExportedPdf exportPdf(StructuredDocument document);
}
