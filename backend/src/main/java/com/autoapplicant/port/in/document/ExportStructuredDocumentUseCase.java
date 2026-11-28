package com.autoapplicant.port.in.document;

import com.autoapplicant.domain.document.ExportedPdf;
import com.autoapplicant.domain.document.design.ResumeDesign;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import java.util.List;

public interface ExportStructuredDocumentUseCase {

    /**
     * The document as a PDF with its page count against its budget (a CV 2 pages, anything else
     * 1). Going over is logged, or refused with {@link IllegalStateException} when the page
     * budget is strict.
     */
    ExportedPdf exportPdf(StructuredDocument document);

    /** The resume builder's design as a PDF, held to the CV budget the same way. */
    ExportedPdf exportPdf(ResumeDesign design);

    /**
     * The resume builder's design as one SVG document per page, from the same layout as
     * {@link #exportPdf(ResumeDesign)}, so a preview of these pages is the PDF.
     */
    List<String> previewPages(ResumeDesign design);
}
