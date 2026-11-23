package com.autoapplicant.adapter.render;

import static com.autoapplicant.domain.document.design.ResumeDesigns.BOLD_PHRASE;
import static com.autoapplicant.domain.document.design.ResumeDesigns.BULLET;
import static com.autoapplicant.domain.document.design.ResumeDesigns.EXPERIENCE_HEADING;
import static com.autoapplicant.domain.document.design.ResumeDesigns.NAME;
import static com.autoapplicant.domain.document.design.ResumeDesigns.ROLE;
import static com.autoapplicant.domain.document.design.ResumeDesigns.SKILL;
import static com.autoapplicant.domain.document.design.ResumeDesigns.TITLE;
import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.domain.document.DocumentType;
import com.autoapplicant.domain.document.design.ResumeDesign;
import com.autoapplicant.domain.document.design.ResumeDesigns;
import com.autoapplicant.domain.document.structured.DocumentIdentity;
import com.autoapplicant.domain.document.structured.DocumentRenderOptions;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import com.autoapplicant.port.out.document.DocumentRendererPort.RenderedPdf;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The designed CV and letter are real text PDFs: what an applicant tracking system extracts is
 * the Danish text the user wrote, in every layout the builder offers. Rendered by the real CLI.
 */
class TypstResumeRenderTest {

    private static final String LETTER_OPENING = "Kære rekrutteringsteam";
    private static final String LETTER_PROMISE = "Jeg løser jeres behov for klar kommunikation";

    /**
     * Bug caught: a layout leaves out the name, a heading, a role or a rich-text bullet, or lays
     * the page out as an image so nothing can be extracted.
     */
    @ParameterizedTest
    @ValueSource(strings = {"classic", "modern", "modern-2col", "minimal", "executive", "creative"})
    void everyLayoutIsATextPdfOfTheWholeCv(String layout) throws IOException {
        RenderedPdf pdf = TypstFixtures.adapter().renderPdf(ResumeDesigns.danish(layout));

        String text = flowed(TypstFixtures.text(pdf.bytes()));
        assertThat(text).contains(NAME, TITLE, EXPERIENCE_HEADING.toUpperCase(Locale.ROOT), ROLE, BULLET,
                BOLD_PHRASE, SKILL);
        assertThat(pdf.pageCount()).isEqualTo(1);
    }

    /** Bug caught: preview and PDF compile from different data or templates, so they drift apart. */
    @Test
    void previewHasAsManyPagesAsThePdf() {
        ResumeDesign design = ResumeDesigns.danish("modern-2col");

        RenderedPdf pdf = TypstFixtures.adapter().renderPdf(design);
        List<byte[]> pages = TypstFixtures.adapter().renderSvgPages(design);

        assertThat(pages).hasSize(pdf.pageCount());
        assertThat(new String(pages.get(0), StandardCharsets.UTF_8)).startsWith("<svg");
    }

    /**
     * Bug caught: user text reaches Typst as markup, so a name or summary containing Typst code runs it
     * (here, a read of a file outside the content) instead of printing it.
     */
    @Test
    void typstMarkupInUserTextIsPrintedNotRun() throws IOException {
        String hostile = "#read(\"/etc/passwd\") *bold* $x$";
        ResumeDesign base = ResumeDesigns.withPerson("classic",
                new ResumeDesign.Person(hostile, TITLE, null, null, null), null, null);
        ResumeDesign design = withSummary(base, "<p>" + hostile + " #panic()</p>");

        String text = flowed(TypstFixtures.text(TypstFixtures.adapter().renderPdf(design).bytes()));

        assertThat(text).contains(hostile, hostile + " #panic()");
    }

    /**
     * Bug caught: a crafted colour or font travels into the template and aborts the compile, so
     * the user gets no PDF at all.
     */
    @Test
    void craftedThemeValuesFallBackInsteadOfFailing() throws IOException {
        ResumeDesign.Theme hostile = new ResumeDesign.Theme("#zzzzzz\") + read(\"x", "red; }", "Comic\" + 1",
                1e9, -4.0, Double.NaN);
        ResumeDesign design = ResumeDesigns.withPerson("nonexistent-layout",
                new ResumeDesign.Person(NAME, TITLE, null, null, null), hostile, null);

        RenderedPdf pdf = TypstFixtures.adapter().renderPdf(design);

        assertThat(flowed(TypstFixtures.text(pdf.bytes()))).contains(NAME);
    }

    /** Bug caught: the builder's photo is never staged, so a designed CV loses it. */
    @Test
    void uploadedPhotoIsEmbedded(@TempDir Path uploads) throws IOException {
        PdfTestImages.writePng(uploads.resolve("me.png"));
        ResumeDesign design = ResumeDesigns.withPerson("classic",
                new ResumeDesign.Person(NAME, TITLE, null, null, null), null, "/uploads/me.png");

        RenderedPdf pdf = TypstFixtures.adapter(uploads).renderPdf(design);

        assertThat(PdfTestImages.imagesOnFirstPage(pdf.bytes())).isPositive();
    }

    /**
     * Bug caught: a designed letter's editor HTML is dropped or printed as tags, or the chosen
     * letter style falls back to the plain layout.
     */
    @ParameterizedTest
    @ValueSource(strings = {"letter-editorial", "letter-classic", "letter-bold"})
    void designedLetterIsATextPdfOfItsBody(String templateId) throws IOException {
        String body = "<p>" + LETTER_OPENING + "</p><p>" + LETTER_PROMISE + " <strong>hver dag</strong>.</p>";
        StructuredDocument letter = new StructuredDocument(null, DocumentType.COVER_LETTER, "DESIGNED", templateId,
                new DocumentIdentity(NAME, TITLE, "mette@example.dk", null, null, null, null, null, null),
                DocumentRenderOptions.defaults(), List.of(), body, null, null);

        RenderedPdf pdf = TypstFixtures.adapter().renderPdf(letter);

        String text = TypstFixtures.text(pdf.bytes());
        assertThat(text).contains(NAME, LETTER_OPENING, LETTER_PROMISE, "hver dag").doesNotContain("<p>", "strong");
        assertThat(pdf.pageCount()).isEqualTo(1);
    }

    /** Bug caught: the letter style is ignored, so a designed letter renders on the ATS layout's white page. */
    @Test
    void letterStyleChangesThePage() throws IOException {
        byte[] editorial = TypstFixtures.adapter().renderPdf(letter("letter-editorial")).bytes();
        byte[] plain = TypstFixtures.adapter().renderPdf(letter("application-ats")).bytes();

        assertThat(TypstFixtures.fontsOnFirstPage(editorial)).anyMatch(font -> font.contains("Inter"));
        assertThat(TypstFixtures.fontsOnFirstPage(plain)).noneMatch(font -> font.contains("Inter"));
    }

    /** The text as a parser reads it across line breaks; narrow columns wrap long names. */
    private static String flowed(String text) {
        return text.replaceAll("\\s+", " ");
    }

    private static ResumeDesign withSummary(ResumeDesign d, String summary) {
        return new ResumeDesign(d.layout(), d.pageSize(), d.theme(), d.photo(), d.showSkillLevel(), d.labels(),
                d.person(), summary, d.experience(), d.education(), d.projects(), d.skillGroups(), d.languages(),
                d.certifications(), d.strengths(), d.socials(), d.customSections(), d.mainOrder(), d.sideOrder(),
                d.hidden(), d.sectionStyles());
    }

    private static StructuredDocument letter(String templateId) {
        return new StructuredDocument(null, DocumentType.COVER_LETTER, "DESIGNED", templateId,
                new DocumentIdentity(NAME, TITLE, null, null, null, null, null, null, null),
                DocumentRenderOptions.defaults(), List.of(), LETTER_OPENING, null, null);
    }
}
