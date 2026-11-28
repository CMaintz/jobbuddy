package com.autoapplicant.adapter.render;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.domain.document.DocumentType;
import com.autoapplicant.domain.document.design.ResumeDesign;
import com.autoapplicant.domain.document.design.ResumeDesigns;
import com.autoapplicant.domain.document.structured.DocumentIdentity;
import com.autoapplicant.domain.document.structured.DocumentRenderOptions;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Each builder setting she can pick visibly changes the rendered CV. Rendered by the real CLI. */
class TypstDesignSettingsRenderTest {

    private static final String SUMMARY = "<p>Erfaren konsulent.</p>";
    private static final String ALIGNED = "Højrestillet linje";
    private static final ResumeDesign.Photo NO_PHOTO = new ResumeDesign.Photo(null, "circle", null, null);
    /** A clear red, so its tints stand apart from the blue-grey ink. */
    private static final String SECTION_RED = "#c0392b";
    /** Typst writes mixed colours as oklab(L a b); a > 0 is a red hue, which the default greys never have. */
    private static final Pattern OKLAB_FILL = Pattern.compile("fill=\"oklab\\([0-9.]+% (-?[0-9.]+) ");
    private static final double RED_HUE = 0.05;

    /**
     * Bug caught: a section colour reaches only the section's plain text, while its subtitles,
     * dates and descriptions keep the default greys, so most of the section ignores the colour.
     */
    @Test
    void sectionColourTintsTheSectionsDetailText() {
        Map<String, ResumeDesign.SectionStyle> red =
                Map.of("experience", new ResumeDesign.SectionStyle(null, false, false, SECTION_RED, 1.0));

        String page = svg(design("classic", SUMMARY, NO_PHOTO, "zap", "linkedin", red));

        assertThat(oklabHues(page)).anyMatch(a -> a > RED_HUE);
    }

    /** Bug caught: the layouts draw one fixed marker, so the strength icon she picks never shows. */
    @ParameterizedTest
    @ValueSource(strings = {"classic", "modern-2col", "creative"})
    void pickedStrengthIconChangesTheCv(String layout) {
        String zap = svg(design(layout, SUMMARY, NO_PHOTO, "zap", "linkedin", Map.of()));
        String award = svg(design(layout, SUMMARY, NO_PHOTO, "award", "linkedin", Map.of()));

        assertThat(zap.equals(award)).as("the CV looks the same whichever strength icon she picks").isFalse();
    }

    /** Bug caught: links print as bare text, so the platform icon she picks never shows. */
    @ParameterizedTest
    @ValueSource(strings = {"classic", "modern", "modern-2col", "executive", "creative"})
    void pickedSocialIconChangesTheCv(String layout) {
        String github = svg(design(layout, SUMMARY, NO_PHOTO, "zap", "github", Map.of()));
        String globe = svg(design(layout, SUMMARY, NO_PHOTO, "zap", "globe", Map.of()));

        assertThat(github.equals(globe)).as("the CV looks the same whichever link icon she picks").isFalse();
    }

    /** Bug caught: the editor's alignment is dropped on the way to Typst, so right-aligned text prints left. */
    @Test
    void editorAlignmentMovesTheText() throws IOException {
        float left = PdfLayout.textX(pdf(design("classic", "<p>" + ALIGNED + "</p>", NO_PHOTO, "zap", "link",
                Map.of())), ALIGNED);
        float right = PdfLayout.textX(pdf(design("classic", "<p style=\"text-align: right\">" + ALIGNED + "</p>",
                NO_PHOTO, "zap", "link", Map.of())), ALIGNED);

        assertThat(right).isGreaterThan(left + 100);
    }

    /** Bug caught: a designed letter drops the alignment the letter editor wrote. */
    @Test
    void letterEditorAlignmentMovesTheText() throws IOException {
        float left = PdfLayout.textX(letter("<p>" + ALIGNED + "</p>"), ALIGNED);
        float centred = PdfLayout.textX(letter("<p style=\"text-align: center\">" + ALIGNED + "</p>"), ALIGNED);

        assertThat(centred).isGreaterThan(left + 100);
    }

    /** Bug caught: the editor's font mark is dropped, so a phrase set in another font prints in the CV's font. */
    @Test
    void editorFontMarkPrintsInThatFont() throws IOException {
        String summary = "<p>Inter overalt, men <span style=\"font-family: &quot;Times New Roman&quot;, serif\">"
                + "denne sætning</span> står med serif.</p>";

        byte[] pdf = pdf(design("classic", summary, NO_PHOTO, "zap", "link", Map.of()));

        assertThat(TypstFixtures.fontsOnFirstPage(pdf)).anyMatch(font -> font.contains("+LiberationSerif"));
    }

    /** Bug caught: a layout centres its photo whatever she picks, so left and right look the same. */
    @ParameterizedTest
    @ValueSource(strings = {"classic", "modern", "modern-2col", "executive", "creative"})
    void photoPlacementMovesThePhoto(String layout, @TempDir Path uploads) throws IOException {
        PdfTestImages.writePng(uploads.resolve("me.png"));

        float left = photoX(uploads, layout, "left");
        float right = photoX(uploads, layout, "right");

        assertThat(right).isGreaterThan(left + 20);
    }

    private static float photoX(Path uploads, String layout, String placement) throws IOException {
        ResumeDesign.Photo photo = new ResumeDesign.Photo("/uploads/me.png", "square", null, placement);
        byte[] pdf = TypstFixtures.adapter(uploads).renderPdf(design(layout, SUMMARY, photo, "zap", "link", Map.of()))
                .bytes();
        List<Float> xs = PdfLayout.imageXs(pdf);
        assertThat(xs).hasSize(1);
        return xs.get(0);
    }

    private static ResumeDesign design(String layout, String summary, ResumeDesign.Photo photo, String strengthIcon,
                                       String socialIcon, Map<String, ResumeDesign.SectionStyle> styles) {
        return ResumeDesigns.with(ResumeDesigns.danish(layout), summary, photo, strengthIcon, socialIcon, styles);
    }

    private static String svg(ResumeDesign design) {
        return new String(TypstFixtures.adapter().renderSvgPages(design).get(0), StandardCharsets.UTF_8);
    }

    private static byte[] pdf(ResumeDesign design) {
        return TypstFixtures.adapter().renderPdf(design).bytes();
    }

    private static byte[] letter(String body) {
        StructuredDocument letter = new StructuredDocument(null, DocumentType.COVER_LETTER, "DESIGNED",
                "letter-classic", new DocumentIdentity(ResumeDesigns.NAME, ResumeDesigns.TITLE, null, null, null,
                null, null, null, null), DocumentRenderOptions.defaults(), List.of(), body, null, null);
        return TypstFixtures.adapter().renderPdf(letter).bytes();
    }

    private static List<Double> oklabHues(String svg) {
        Matcher m = OKLAB_FILL.matcher(svg);
        List<Double> hues = new ArrayList<>();
        while (m.find()) hues.add(Double.parseDouble(m.group(1)));
        return hues;
    }
}
