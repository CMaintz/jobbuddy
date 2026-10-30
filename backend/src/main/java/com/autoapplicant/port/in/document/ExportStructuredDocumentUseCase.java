package com.autoapplicant.port.in.document;

import com.autoapplicant.domain.document.structured.StructuredDocument;

public interface ExportStructuredDocumentUseCase {

    /**
     * The document as PDF bytes. A CV over 2 pages, or any other document over 1, is logged,
     * or refused with {@link IllegalStateException} when the page budget is strict.
     */
    byte[] exportPdf(StructuredDocument document);
}
