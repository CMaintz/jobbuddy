package com.autoapplicant.adapter.render;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.domain.document.structured.StructuredDocument;
import com.autoapplicant.port.out.document.DocumentRendererPort.RenderedPdf;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * The SVG preview and the PDF must be the same layout: same pages, every PDF glyph at the same
 * spot (within 0.2pt) in the SVG of its page. Catches a preview rendered with other fonts,
 * options or data than the PDF, which would show the user a document they will not get.
 */
class TypstLayoutIdentityTest {

    private static final double TOLERANCE_PT = 0.2;
    private static final Pattern TRANSLATE = Pattern.compile("translate\\(([-\\d.e]+)(?:[ ,]+([-\\d.e]+))?\\)");
    private static final Pattern MATRIX = Pattern.compile("matrix\\(([-\\d.e ]+)\\)");
    private static final int MATRIX_D = 3;
    private static final int MATRIX_E = 4;
    private static final int MATRIX_F = 5;

    private record Glyph(double x, double y) {
        boolean near(Glyph other) {
            return Math.abs(x - other.x) <= TOLERANCE_PT && Math.abs(y - other.y) <= TOLERANCE_PT;
        }
    }

    @Test
    void multiPageLetterHasIdenticalGlyphPositionsInPdfAndSvg() throws Exception {
        assertSameLayout(TypstFixtures.letter("A paragraph with some words in it.\n\n".repeat(90)));
    }

    @Test
    void cvHasIdenticalGlyphPositionsInPdfAndSvg() throws Exception {
        assertSameLayout(TypstFixtures.cv(TypstFixtures.NAME, null));
    }

    private static void assertSameLayout(StructuredDocument document) throws Exception {
        TypstRendererAdapter adapter = TypstFixtures.adapter();
        RenderedPdf pdf = adapter.renderPdf(document);
        List<byte[]> svgPages = adapter.renderSvgPages(document);

        assertThat(svgPages).hasSize(pdf.pageCount());
        List<List<Glyph>> pdfPages = pdfGlyphs(pdf.bytes());
        for (int page = 0; page < svgPages.size(); page++) {
            List<Glyph> svg = svgGlyphs(svgPages.get(page));
            List<Glyph> unmatched = pdfPages.get(page).stream()
                    .filter(glyph -> svg.stream().noneMatch(glyph::near))
                    .toList();
            assertThat(pdfPages.get(page)).as("text glyphs on page %d", page + 1).isNotEmpty();
            assertThat(unmatched).as("PDF glyphs without an SVG glyph on page %d", page + 1).isEmpty();
        }
    }

    /** Baseline origin of every non-space glyph, per page, top-left origin like the SVG. */
    private static List<List<Glyph>> pdfGlyphs(byte[] pdf) throws IOException {
        try (PDDocument document = PDDocument.load(pdf)) {
            List<List<Glyph>> pages = new ArrayList<>();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                List<Glyph> glyphs = new ArrayList<>();
                PDFTextStripper stripper = new PDFTextStripper() {
                    @Override
                    protected void writeString(String text, List<TextPosition> positions) {
                        for (TextPosition position : positions) {
                            if (!position.getUnicode().isBlank()) {
                                glyphs.add(new Glyph(position.getXDirAdj(), position.getYDirAdj()));
                            }
                        }
                    }
                };
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                stripper.getText(document);
                pages.add(glyphs);
            }
            return pages;
        }
    }

    /** Placement of every {@code <use>}, accumulating the group transforms Typst emits. */
    private static List<Glyph> svgGlyphs(byte[] svg) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Element root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(svg)).getDocumentElement();
        List<Glyph> glyphs = new ArrayList<>();
        walk(root, 0, 0, 1, glyphs);
        return glyphs;
    }

    private static void walk(Element element, double tx, double ty, double sy, List<Glyph> out) {
        double x = tx;
        double y = ty;
        double scaleY = sy;
        Matcher translate = TRANSLATE.matcher(element.getAttribute("transform"));
        if (translate.lookingAt()) {
            x += Double.parseDouble(translate.group(1));
            y += scaleY * (translate.group(2) == null ? 0 : Double.parseDouble(translate.group(2)));
        }
        Matcher matrix = MATRIX.matcher(element.getAttribute("transform"));
        if (matrix.lookingAt()) {
            String[] m = matrix.group(1).trim().split("\\s+");
            x += Double.parseDouble(m[MATRIX_E]);
            y += scaleY * Double.parseDouble(m[MATRIX_F]);
            scaleY *= Double.parseDouble(m[MATRIX_D]);
        }
        if ("use".equals(element.getLocalName() != null ? element.getLocalName() : element.getTagName())
                && element.hasAttribute("x")) {
            out.add(new Glyph(x + Double.parseDouble(element.getAttribute("x")), y));
        }
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element childElement) walk(childElement, x, y, scaleY, out);
        }
    }
}
