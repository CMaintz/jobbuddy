package com.autoapplicant.usecase.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.autoapplicant.domain.document.structured.CareerProfileForAi;
import com.autoapplicant.domain.document.structured.DocumentTheme;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import com.autoapplicant.domain.document.structured.StructuredDocumentItem;
import com.autoapplicant.domain.document.structured.StructuredDocumentSection;
import com.autoapplicant.domain.document.structured.TailoredCvContent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The honesty guarantee for custom sections: the AI may rewrite item text and drop items, but an
 * item it invents (unknown sourceId) is dropped, and a section it omits is preserved untailored —
 * so tailoring can never fabricate or silently lose the user's custom content.
 */
class CvDocumentAssemblerCustomSectionsTest {

    private final CvDocumentAssembler assembler =
            new CvDocumentAssembler(mock(AtsReportBuilder.class), mock(KeywordCoverageCalculator.class));

    @Test
    void tailoredCustomSectionsKeepSourceItemsRewriteTextAndDropFabrications() {
        CareerProfileForAi source = sourceWith(List.of(
                custom("s1", "Awards", List.of(item("i1", "Won X"), item("i2", "Won Y"))),
                custom("s2", "Volunteering", List.of(item("v1", "Helped out")))));

        // The AI rewrites i1, invents i9, renames the heading, and omits section s2 entirely.
        TailoredCvContent tailored = tailoredWith(List.of(
                custom("s1", "Trophies", List.of(
                        item("i1", "Won X, quantified"), item("i9", "FABRICATED AWARD")))));

        StructuredDocument doc = assembler.assemble(null, null, null, List.of(), source, tailored,
                "ATS", "cv-ats-classic", false, DocumentTheme.defaults(), null, "English", null, "");

        List<StructuredDocumentSection> custom = doc.sections().stream()
                .filter(s -> StructuredDocumentSection.TYPE_CUSTOM.equals(s.type())).toList();
        assertThat(custom).extracting(StructuredDocumentSection::id).containsExactly("s1", "s2");

        StructuredDocumentSection s1 = custom.get(0);
        assertThat(s1.heading()).isEqualTo("Awards"); // user's heading, AI rename ignored
        assertThat(s1.items()).extracting(StructuredDocumentItem::title)
                .containsExactly("Won X, quantified"); // i1 rewritten; i9 fabrication dropped
        assertThat(s1.items()).extracting(StructuredDocumentItem::sourceId).containsExactly("i1");

        // s2 was omitted by the AI, so it survives untailored rather than being lost.
        StructuredDocumentSection s2 = custom.get(1);
        assertThat(s2.heading()).isEqualTo("Volunteering");
        assertThat(s2.items()).extracting(StructuredDocumentItem::title).containsExactly("Helped out");
    }

    private static StructuredDocumentSection custom(String id, String heading, List<StructuredDocumentItem> items) {
        return new StructuredDocumentSection(id, StructuredDocumentSection.TYPE_CUSTOM, heading, null, items);
    }

    private static StructuredDocumentItem item(String id, String title) {
        return new StructuredDocumentItem(id, title, null, null, null, null,
                List.of(), List.of(), List.of(), List.of(), null);
    }

    private static CareerProfileForAi sourceWith(List<StructuredDocumentSection> customSections) {
        return new CareerProfileForAi(null, null, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null,
                null, Map.of(), null, customSections);
    }

    private static TailoredCvContent tailoredWith(List<StructuredDocumentSection> customSections) {
        return new TailoredCvContent(null, List.of(), List.of(), List.of(), List.of(), List.of(),
                customSections, List.of());
    }
}
