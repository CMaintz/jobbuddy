package com.autoapplicant.adapter.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.autoapplicant.domain.document.structured.DocumentTheme;
import com.autoapplicant.port.out.document.DocumentRenderException;
import com.autoapplicant.port.out.document.DocumentRendererPort.RenderedPdf;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** The built-in template, rendered by the real Typst CLI, produces a text PDF of the whole document. */
class TypstRendererAdapterTest {

    private static final String CJK_NAME = "王小明";
    private static final String NOTO_CJK = "NotoSansSC";
    private static final String CARLITO = "Carlito";
    private static final String TOO_LONG_LETTER = "Paragraph.\n\n".repeat(120);

    /** Bug caught: the template drops the name, a section heading or the item titles from the text layer. */
    @Test
    void cvPdfTextLayerCarriesNameHeadingsAndItemTitles() throws IOException {
        RenderedPdf pdf = TypstFixtures.adapter().renderPdf(TypstFixtures.cv(TypstFixtures.NAME, null));

        assertThat(TypstFixtures.text(pdf.bytes())).contains(TypstFixtures.NAME, TypstFixtures.HEADLINE,
                TypstFixtures.EXPERIENCE, TypstFixtures.SKILLS, TypstFixtures.ROLE, TypstFixtures.COMPANY,
                TypstFixtures.SKILL);
        assertThat(pdf.pageCount()).isEqualTo(1);
    }

    /** Bug caught: a letter is laid out as a CV and its body text disappears. */
    @Test
    void letterPdfCarriesItsParagraphs() throws IOException {
        RenderedPdf pdf = TypstFixtures.adapter().renderPdf(TypstFixtures.letter("Dear team,\n\nI am writing."));

        assertThat(TypstFixtures.text(pdf.bytes())).contains("Dear team,", "I am writing.");
    }

    /** Bug caught: the page budget reads a wrong count because the adapter does not measure the real PDF. */
    @Test
    void pageCountIsTheRenderedPdfsPageCount() {
        RenderedPdf pdf = TypstFixtures.adapter().renderPdf(TypstFixtures.letter(TOO_LONG_LETTER));

        assertThat(pdf.pageCount()).isGreaterThan(1);
    }

    /** Bug caught: an invalid theme colour reaches {@code rgb()} and aborts the compile. */
    @Test
    void malformedThemeColourFallsBackInsteadOfFailingTheRender() throws IOException {
        DocumentTheme hostile = new DocumentTheme("#zzzzzz\") + read(\"/etc/passwd", "red", "Arial", "huge");

        RenderedPdf pdf = TypstFixtures.adapter().renderPdf(TypstFixtures.cv(TypstFixtures.NAME, hostile));

        assertThat(TypstFixtures.text(pdf.bytes())).contains(TypstFixtures.NAME);
    }

    /** Bug caught: a whitelisted theme font is not mapped to its bundled stand-in. */
    @Test
    void calibriThemeRendersWithBundledCarlito() throws IOException {
        DocumentTheme calibri = new DocumentTheme("#18324a", "#cbd8e3", "Calibri", "normal");

        RenderedPdf pdf = TypstFixtures.adapter().renderPdf(TypstFixtures.cv(TypstFixtures.NAME, calibri));

        assertThat(TypstFixtures.fontsOnFirstPage(pdf.bytes())).anyMatch(font -> font.contains(CARLITO));
    }

    /** Bug caught: no bundled font covers CJK, so the name renders as missing-glyph boxes. */
    @Test
    void cjkNameRendersWithTheNotoFallback() throws IOException {
        RenderedPdf pdf = TypstFixtures.adapter().renderPdf(TypstFixtures.cv(CJK_NAME, null));

        assertThat(TypstFixtures.fontsOnFirstPage(pdf.bytes())).anyMatch(font -> font.contains(NOTO_CJK));
        assertThat(TypstFixtures.text(pdf.bytes())).contains(CJK_NAME);
    }

    /** Bug caught: a failed render leaves the user's data behind in a temp directory. */
    @Test
    void workspaceIsDeletedWhenTheRenderFails() throws IOException {
        Set<Path> before = workspaces();
        byte[] truncatedPng = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 2, 3};
        String corruptPng = "data:image/png;base64," + Base64.getEncoder().encodeToString(truncatedPng);

        assertThatThrownBy(() -> TypstFixtures.adapter()
                        .renderPdf(TypstFixtures.designedCvWithPhoto(corruptPng)))
                .isInstanceOf(DocumentRenderException.class);
        assertThat(workspaces()).isSubsetOf(before);
    }

    private static Set<Path> workspaces() throws IOException {
        try (Stream<Path> entries = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return entries.filter(p -> p.getFileName().toString().startsWith("typst-render-"))
                    .collect(Collectors.toSet());
        }
    }
}
