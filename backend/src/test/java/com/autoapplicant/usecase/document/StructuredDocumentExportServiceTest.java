package com.autoapplicant.usecase.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.autoapplicant.domain.document.DocumentType;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import com.autoapplicant.port.out.document.DocumentRendererPort;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The page budget (CV at most 2 pages, anything else 1) is checked against the page count the
 * renderer reports. Catches a swapped CV/letter budget, an off-by-one at the limit, and a strict
 * flag that is ignored.
 */
class StructuredDocumentExportServiceTest {

    private static final byte[] PDF = {1, 2, 3};

    @Test
    void strictBudgetRefusesACvOverTwoPages() {
        StructuredDocumentExportService service = service(3, true);

        assertThatThrownBy(() -> service.exportPdf(document(DocumentType.CV)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("3 pages");
    }

    @Test
    void strictBudgetAcceptsACvOfExactlyTwoPages() {
        assertThat(service(2, true).exportPdf(document(DocumentType.CV))).isEqualTo(PDF);
    }

    @Test
    void strictBudgetRefusesALetterOverOnePage() {
        StructuredDocumentExportService service = service(2, true);

        assertThatThrownBy(() -> service.exportPdf(document(DocumentType.COVER_LETTER)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void lenientBudgetStillReturnsTheOverlongPdf() {
        assertThat(service(5, false).exportPdf(document(DocumentType.CV))).isEqualTo(PDF);
    }

    private static StructuredDocumentExportService service(int pages, boolean strict) {
        return new StructuredDocumentExportService(new FixedPageRenderer(pages), strict);
    }

    private static StructuredDocument document(DocumentType type) {
        return new StructuredDocument(null, type, "ATS", "ats-classic", null, null, List.of(), "", null);
    }

    private record FixedPageRenderer(int pages) implements DocumentRendererPort {

        @Override
        public RenderedPdf renderPdf(StructuredDocument document) {
            return new RenderedPdf(PDF, pages);
        }

        @Override
        public List<byte[]> renderSvgPages(StructuredDocument document) {
            throw new UnsupportedOperationException("not used by the PDF export");
        }
    }
}
