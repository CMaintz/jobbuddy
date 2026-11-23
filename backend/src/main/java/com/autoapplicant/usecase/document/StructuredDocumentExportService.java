package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.DocumentType;
import com.autoapplicant.domain.document.ExportedPdf;
import com.autoapplicant.domain.document.PageBudget;
import com.autoapplicant.domain.document.design.ResumeDesign;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import com.autoapplicant.port.in.document.ExportStructuredDocumentUseCase;
import com.autoapplicant.port.out.document.DocumentRendererPort;
import com.autoapplicant.port.out.document.DocumentRendererPort.RenderedPdf;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StructuredDocumentExportService implements ExportStructuredDocumentUseCase {

    private static final Logger log = LoggerFactory.getLogger(StructuredDocumentExportService.class);

    private final DocumentRendererPort renderer;
    private final boolean pageBudgetStrict;

    public StructuredDocumentExportService(
            DocumentRendererPort renderer,
            @Value("${app.pdf.page-budget.strict:false}") boolean pageBudgetStrict) {
        this.renderer = renderer;
        this.pageBudgetStrict = pageBudgetStrict;
    }

    @Override
    public ExportedPdf exportPdf(StructuredDocument document) {
        return budgeted(renderer.renderPdf(document), document.documentType());
    }

    @Override
    public ExportedPdf exportPdf(ResumeDesign design) {
        return budgeted(renderer.renderPdf(design), DocumentType.CV);
    }

    @Override
    public List<String> previewPages(ResumeDesign design) {
        return renderer.renderSvgPages(design).stream()
                .map(page -> new String(page, StandardCharsets.UTF_8))
                .toList();
    }

    private ExportedPdf budgeted(RenderedPdf pdf, DocumentType type) {
        PageBudget budget = PageBudget.of(type, pdf.pageCount());
        enforce(budget);
        return new ExportedPdf(pdf.bytes(), Optional.of(budget));
    }

    /** Measures the rendered page count; the layout is never squeezed to fit. */
    private void enforce(PageBudget budget) {
        if (!budget.overBudget()) return;
        String msg = "PDF page budget exceeded: rendered " + budget.pageCount()
                + " pages (max " + budget.budget() + ")";
        if (pageBudgetStrict) throw new IllegalStateException(msg);
        log.warn(msg);
    }
}
