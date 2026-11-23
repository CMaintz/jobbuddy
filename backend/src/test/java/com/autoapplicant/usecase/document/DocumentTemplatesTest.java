package com.autoapplicant.usecase.document;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The template decides the export format; the user never picks ATS or DESIGNED separately. A
 * wrong mapping ships a designed layout to an applicant tracking system that cannot parse it.
 */
class DocumentTemplatesTest {

    @Test
    void anAtsTemplateExportsAsAts() {
        assertThat(DocumentTemplates.exportModeFromTemplate("cv-ats-classic")).isEqualTo("ATS");
        assertThat(DocumentTemplates.exportModeFromTemplate("CV-ATS-Compact")).isEqualTo("ATS");
    }

    @Test
    void anyOtherTemplateExportsAsDesigned() {
        assertThat(DocumentTemplates.exportModeFromTemplate("cv-modern")).isEqualTo("DESIGNED");
    }

    @Test
    void noTemplateFallsBackToTheParseableFormat() {
        assertThat(DocumentTemplates.exportModeFromTemplate(null)).isEqualTo("ATS");
        // The service resolves a blank id to its default before deriving the mode.
        assertThat(DocumentTemplates.exportModeFromTemplate(
                DocumentTemplates.resolveTemplate("  ", "cv-ats-classic"))).isEqualTo("ATS");
    }
}
