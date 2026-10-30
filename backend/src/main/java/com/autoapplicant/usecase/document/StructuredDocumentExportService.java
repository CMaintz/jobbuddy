package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.DocumentType;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import com.autoapplicant.port.in.document.ExportStructuredDocumentUseCase;
import com.autoapplicant.port.out.document.DocumentRendererPort;
import com.autoapplicant.port.out.document.DocumentRendererPort.RenderedPdf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StructuredDocumentExportService implements ExportStructuredDocumentUseCase {

    private static final Logger log = LoggerFactory.getLogger(StructuredDocumentExportService.class);

    private static final int CV_PAGE_BUDGET = 2;
    private static final int OTHER_PAGE_BUDGET = 1;

    private final DocumentRendererPort renderer;
    private final boolean pageBudgetStrict;

    public StructuredDocumentExportService(
            DocumentRendererPort renderer,
            @Value("${app.pdf.page-budget.strict:false}") boolean pageBudgetStrict) {
        this.renderer = renderer;
        this.pageBudgetStrict = pageBudgetStrict;
    }

    @Override
    public byte[] exportPdf(StructuredDocument document) {
        RenderedPdf pdf = renderer.renderPdf(document);
        enforcePageBudget(pdf.pageCount(), document);
        return pdf.bytes();
    }

    /** Measures the rendered page count; the layout is never squeezed to fit. */
    private void enforcePageBudget(int pages, StructuredDocument document) {
        boolean cv = document.documentType() == DocumentType.CV;
        int max = cv ? CV_PAGE_BUDGET : OTHER_PAGE_BUDGET;
        if (pages <= max) return;
        String msg = "PDF page budget exceeded: " + (cv ? "CV" : "document")
                + " rendered " + pages + " pages (max " + max + ")";
        if (pageBudgetStrict) throw new IllegalStateException(msg);
        log.warn(msg);
    }
}
