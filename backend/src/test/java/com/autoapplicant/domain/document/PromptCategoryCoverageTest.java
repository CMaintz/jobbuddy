package com.autoapplicant.domain.document;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every generated document type must have a prompt category it can resolve a persona through.
 *
 * <p>Default-persona lookup matches a {@link DocumentType} name against a template's
 * {@link PromptCategory}, and a mismatch fails silently: the generation falls back to the generic
 * built-in persona and nothing reports it. That is what happened to unsolicited applications and
 * follow-ups, which were written in the application voice — the one voice that fits neither a
 * proposal nor a three-sentence nudge.
 *
 * <p>So a new document type either gets a category of the same name, or is listed here as
 * deliberately having none.
 */
class PromptCategoryCoverageTest {

    /**
     * Document types that resolve their persona elsewhere: the CV goes through CV_TAILORING rather
     * than a category of its own, application text through APPLICATION, and the analysis report is
     * assembled from a scored structure rather than written by a persona.
     */
    private static final Set<DocumentType> NO_PERSONA_BY_DESIGN =
            EnumSet.of(DocumentType.CV, DocumentType.CV_ANALYSIS_REPORT, DocumentType.APPLICATION_TEXT);

    @Test
    void every_generated_document_type_has_a_matching_prompt_category() {
        Set<String> categories = EnumSet.allOf(PromptCategory.class).stream()
                .map(Enum::name).collect(Collectors.toSet());

        Set<String> uncovered = EnumSet.allOf(DocumentType.class).stream()
                .filter(t -> !NO_PERSONA_BY_DESIGN.contains(t))
                .map(Enum::name)
                .filter(name -> !categories.contains(name))
                .collect(Collectors.toSet());

        assertThat(uncovered)
                .as("document types with no PromptCategory of the same name — they would silently "
                        + "fall back to the generic built-in persona")
                .isEmpty();
    }
}
