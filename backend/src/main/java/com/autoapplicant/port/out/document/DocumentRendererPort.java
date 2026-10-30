package com.autoapplicant.port.out.document;

import com.autoapplicant.domain.document.structured.StructuredDocument;
import java.util.List;

/**
 * Lays out a structured document and exports it. The PDF and the SVG pages come from the same
 * layout engine, so a preview built from {@link #renderSvgPages} matches the PDF page for page.
 */
public interface DocumentRendererPort {

    /**
     * Renders the document to a text PDF.
     *
     * @throws DocumentRenderException the layout engine failed, timed out or could not start
     */
    RenderedPdf renderPdf(StructuredDocument document);

    /**
     * Renders the document to one SVG image per page, in page order.
     *
     * @throws DocumentRenderException the layout engine failed, timed out or could not start
     */
    List<byte[]> renderSvgPages(StructuredDocument document);

    /** A rendered PDF and the number of pages it has. */
    record RenderedPdf(byte[] bytes, int pageCount) {}
}
