package com.autoapplicant.usecase.document;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CvSectionLabelsTest {

    @Test
    void danishCvGetsDanishHeadings() {
        CvSectionLabels labels = CvSectionLabels.forLanguage("Danish");
        assertThat(labels.experience()).isEqualTo("Erhvervserfaring");
        assertThat(labels.education()).isEqualTo("Uddannelse");
        assertThat(labels.interests()).isEqualTo("Fritidsinteresser");
    }

    @Test
    void everythingElseKeepsEnglishHeadings() {
        CvSectionLabels labels = CvSectionLabels.forLanguage("English");
        assertThat(labels.experience()).isEqualTo("Experience");
        assertThat(CvSectionLabels.forLanguage(null).education()).isEqualTo("Education");
    }

    @Test
    void theReferencesLineIsDanishOnly() {
        // "References available on request" is dead weight on an English CV; the Danish line is expected.
        assertThat(CvSectionLabels.forLanguage("Dansk").referencesNote())
                .isEqualTo("Referencer oplyses gerne efter aftale.");
        assertThat(CvSectionLabels.forLanguage("English").referencesNote()).isNull();
        assertThat(CvSectionLabels.forLanguage(null).referencesNote()).isNull();
    }
}
