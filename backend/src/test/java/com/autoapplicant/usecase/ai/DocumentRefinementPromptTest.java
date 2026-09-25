package com.autoapplicant.usecase.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.domain.ai.RefineDocumentRequest;
import com.autoapplicant.usecase.document.GenerationGuardrails;
import org.junit.jupiter.api.Test;

/**
 * Pins the refinement prompt assembly — section order and the exact blank-line joins — so a future
 * edit to the builder cannot silently shift the delivered prompt. Uses small synthetic guardrail
 * blocks; the guardrail text itself is owned and tested elsewhere.
 */
class DocumentRefinementPromptTest {

    private static GenerationGuardrails guardrails() {
        return new GenerationGuardrails("English", "MARKET", "UNTRUSTED", "CLICHE", "HONESTY");
    }

    @Test
    void systemPromptIsEditorPersonaThenUntrustedGuardThenLanguage() {
        String system = DocumentRefinementService.refineSystemPrompt(guardrails());
        assertThat(system)
                .contains("write better, never to claim more")
                .endsWith("\n\nUNTRUSTED\nWrite in English.");
    }

    @Test
    void userPromptJoinsDraftContextRequestAndGuardrailsWithBlankLines() {
        RefineDocumentRequest request = new RefineDocumentRequest(
                null, "DRAFT", "make it punchier", "the job", "English", null);
        String user = DocumentRefinementService.refineUserPrompt(request, guardrails(), null);
        assertThat(user).isEqualTo(
                "## Current Document\nDRAFT\n\n"
                + "## Job Description Context\nthe job\n\n"
                + "## Refinement Request\nmake it punchier\n\n"
                + "HONESTY\n\nMARKET\n\nCLICHE");
    }

    @Test
    void absentPostingAndBlankGuardrailSectionsAreOmitted() {
        RefineDocumentRequest request = new RefineDocumentRequest(
                null, "DRAFT", "tighten it", null, null, null);
        String user = DocumentRefinementService.refineUserPrompt(
                request, new GenerationGuardrails(null, "", "UNTRUSTED", "", "HONESTY"), null);
        assertThat(user).isEqualTo(
                "## Current Document\nDRAFT\n\n## Refinement Request\ntighten it\n\nHONESTY");
    }

    @Test
    void savedSectionGuidanceIsInsertedBeforeTheHonestyRules() {
        RefineDocumentRequest request = new RefineDocumentRequest(
                null, "DRAFT", "tighten it", null, null, "profile");
        String user = DocumentRefinementService.refineUserPrompt(
                request, new GenerationGuardrails(null, "", "UNTRUSTED", "", "HONESTY"),
                "Lead with leadership impact.");
        assertThat(user).isEqualTo(
                "## Current Document\nDRAFT\n\n## Refinement Request\ntighten it\n\n"
                + "## Your Standing Guidance For This Section\nLead with leadership impact.\n"
                + "Apply it within the honesty rules; it never licenses adding facts the draft lacks.\n\n"
                + "HONESTY");
    }
}
