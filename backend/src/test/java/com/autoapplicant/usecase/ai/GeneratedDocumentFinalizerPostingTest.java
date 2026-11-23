package com.autoapplicant.usecase.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.autoapplicant.domain.ai.GenerateDocumentCommand;
import com.autoapplicant.domain.document.structured.ContentGuardFindings;
import com.autoapplicant.usecase.document.ApplicationDocumentAssembler;
import com.autoapplicant.usecase.document.ClicheGuard;
import com.autoapplicant.usecase.document.DocumentFactGuard;
import com.autoapplicant.usecase.document.GeneratedContentGuards;
import com.autoapplicant.usecase.document.GeneratedDocumentWriter;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/** The posting the letter was written from reaches the fact gate on the way out. */
class GeneratedDocumentFinalizerPostingTest {

    private static final String POSTING = "Kommunen har ca. 680 medarbejdere fordelt på 3 lokationer.";

    private final ApplicationDocumentAssembler assembler =
            mock(ApplicationDocumentAssembler.class, Answers.RETURNS_DEEP_STUBS);

    @Test
    void theLetterIsCheckedAgainstThePostingItQuotes() {
        GeneratedContentGuards guards = new GeneratedContentGuards(
                new DocumentFactGuard(), mock(RetractedClaimsGuard.class), new ClicheGuard());
        ReflectionTestUtils.setField(guards, "factGuardEnabled", true);
        ReflectionTestUtils.setField(guards, "factGuardMode", "warn");
        GeneratedDocumentFinalizer finalizer =
                new GeneratedDocumentFinalizer(guards, assembler, mock(GeneratedDocumentWriter.class));
        String letter = "Med ca. 680 medarbejdere har I brug for overblik, og det kræver struktur.";

        finalizer.finalizeDocument(command(), new GenerationInputs(null, null, "{}", POSTING),
                letter, null, "model");

        ArgumentCaptor<ContentGuardFindings> findings = ArgumentCaptor.forClass(ContentGuardFindings.class);
        verify(assembler).assemble(any(), any(), anyString(), any(), any(), any(), findings.capture());
        assertThat(findings.getValue().unsupportedMetrics()).isEmpty();
    }

    private static GenerateDocumentCommand command() {
        return new GenerateDocumentCommand(UUID.randomUUID(), "COVER_LETTER", null, POSTING, null, null,
                null, null, "Danish", false, null, "STANDARD", null);
    }
}
