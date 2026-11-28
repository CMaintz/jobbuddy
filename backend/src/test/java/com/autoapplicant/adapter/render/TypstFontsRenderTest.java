package com.autoapplicant.adapter.render;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.domain.document.design.ResumeDesign;
import com.autoapplicant.domain.document.design.ResumeDesigns;
import java.io.IOException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Every font the builder's picker offers prints in a face of its own, not in a shared stand-in. */
class TypstFontsRenderTest {

    private static final String EMPHASIS = "<p><strong><em>Ledte omlægningen</em></strong> af alt.</p>";

    /**
     * Bug caught: a picked font is mapped to another bundled family (or is missing from the bundle,
     * so Typst silently falls back to the CJK face), and the CV looks the same whatever she picks.
     * The pairs are the builder's FONT_FAMILIES and the PostScript family of the face that prints
     * them; proprietary names print in their metric-compatible clone.
     */
    @ParameterizedTest
    @CsvSource({
        "Inter, Inter", "Roboto, Roboto", "Open Sans, OpenSans", "Lato, Lato", "Nunito, Nunito",
        "Poppins, Poppins", "Montserrat, Montserrat", "Source Sans 3, SourceSans3", "Noto Sans, NotoSans",
        "Georgia, Gelasio", "Times New Roman, LiberationSerif", "Merriweather, Merriweather",
        "Playfair Display, PlayfairDisplay", "Lora, Lora", "Arial, LiberationSans",
        "Courier New, LiberationMono", "JetBrains Mono, JetBrainsMono"})
    void pickedFontPrintsInItsOwnFace(String picked, String postScriptFamily) throws IOException {
        byte[] pdf = TypstFixtures.adapter().renderPdf(withFont(picked)).bytes();

        assertThat(TypstFixtures.fontsOnFirstPage(pdf)).anyMatch(font -> isOfFamily(font, postScriptFamily));
    }

    /** Bug caught: Inter has no bold-italic face, so bold text loses its weight once it is italic too. */
    @ParameterizedTest
    @CsvSource({"Inter, Inter-BoldItalic", "Lato, Lato-BoldItalic", "Merriweather, Merriweather-BoldItalic"})
    void boldItalicTextKeepsItsWeight(String picked, String boldItalicFace) throws IOException {
        byte[] pdf = TypstFixtures.adapter().renderPdf(withFont(picked)).bytes();

        assertThat(TypstFixtures.fontsOnFirstPage(pdf)).anyMatch(font -> font.endsWith("+" + boldItalicFace));
    }

    /**
     * An embedded font is named SUBSET+Family or SUBSET+Family-Style; a bare prefix would let the
     * CJK fallback NotoSansSC pass for NotoSans.
     */
    private static boolean isOfFamily(String font, String family) {
        return font.endsWith("+" + family) || font.contains("+" + family + "-");
    }

    private static ResumeDesign withFont(String font) {
        ResumeDesign d = ResumeDesigns.danish("classic");
        ResumeDesign.Theme t = d.theme();
        return new ResumeDesign(d.layout(), d.pageSize(),
                new ResumeDesign.Theme(t.color(), t.textColor(), font, t.scale(), t.lineSpacing(), t.sectionSpacing()),
                d.photo(), d.showSkillLevel(), d.labels(), d.person(), EMPHASIS, d.experience(), d.education(),
                d.projects(), d.skillGroups(), d.languages(), d.certifications(), d.strengths(), d.socials(),
                d.customSections(), d.mainOrder(), d.sideOrder(), d.hidden(), d.sectionStyles());
    }
}
