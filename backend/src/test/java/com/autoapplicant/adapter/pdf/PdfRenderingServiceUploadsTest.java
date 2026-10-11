package com.autoapplicant.adapter.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The SSRF guard must still let templates use the app's own uploads. CSS references are resolved
 * twice by the renderer, which is how an uploads background or font could silently vanish.
 */
class PdfRenderingServiceUploadsTest {

    private static final String BUNDLED_FONT = "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf";
    private static final String BUNDLED_FONT_NAME = "LiberationSans";

    @TempDir Path uploads;

    @Test
    void cssBackgroundFromUploadsIsEmbedded() throws IOException {
        PdfTestImages.writePng(uploads.resolve("logo.png"));
        String template = "<html><head><style>{{CSS}}</style></head><body>"
                + "<div style=\"width:40px;height:40px\"></div></body></html>";
        String css = "div { background: url(/uploads/logo.png); }";

        byte[] pdf = service().render(template, css, Map.of());

        assertThat(PdfTestImages.imagesOnFirstPage(pdf)).isPositive();
    }

    @Test
    void imgFromUploadsIsEmbedded() throws IOException {
        PdfTestImages.writePng(uploads.resolve("logo.png"));
        String template = "<html><body><img src=\"/uploads/logo.png\"/></body></html>";

        byte[] pdf = service().render(template, "", Map.of());

        assertThat(PdfTestImages.imagesOnFirstPage(pdf)).isPositive();
    }

    @Test
    void fontFaceFromUploadsIsEmbedded() throws IOException {
        try (InputStream font = getClass().getResourceAsStream(BUNDLED_FONT)) {
            Files.copy(font, uploads.resolve("brand.ttf"));
        }
        String template = "<html><head><style>{{CSS}}</style></head><body><p>Hello</p></body></html>";
        String css = "@font-face { font-family: 'Brand'; src: url(/uploads/brand.ttf); }"
                + " p { font-family: 'Brand'; }";

        byte[] pdf = service().render(template, css, Map.of());

        assertThat(fontsOnFirstPage(pdf)).anyMatch(name -> name.contains(BUNDLED_FONT_NAME));
    }

    private PdfRenderingService service() {
        return new PdfRenderingService(new PdfResourcePolicy(uploads));
    }

    private static List<String> fontsOnFirstPage(byte[] pdf) throws IOException {
        try (PDDocument document = PDDocument.load(pdf)) {
            PDResources resources = document.getPage(0).getResources();
            List<String> names = new ArrayList<>();
            for (COSName name : resources.getFontNames()) {
                names.add(resources.getFont(name).getName());
            }
            return names;
        }
    }
}
