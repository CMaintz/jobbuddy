package com.autoapplicant.usecase.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.ai.GenerateDocumentCommand;
import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.domain.document.structured.LetterGateResult;
import com.autoapplicant.domain.document.structured.LetterGateVerdict;
import com.autoapplicant.domain.document.structured.LetterReview;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.usecase.document.DocumentReviewer;
import com.autoapplicant.usecase.document.LetterBrief;
import com.autoapplicant.usecase.document.LetterGateLoop;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The pipeline order for a letter: draft with its needs, review against the needs, gate, then the
 * guards and the save on the gated text, with the gate's review stored alongside.
 */
class ApplicationDocumentServiceLetterGateTest {

    private static final String POSTING = "Vi søger en compliance-medarbejder.";
    private static final List<String> NEEDS = List.of("680 chauffører uden skærm skal følge reglerne",
            "Revisionen må ikke finde huller i dokumentationen", "Ledelsen skal kunne se status");
    private static final Instant DEADLINE = Instant.parse("2026-09-01T12:15:00Z");

    private final ChatProviderPort provider = mock(ChatProviderPort.class);
    private final ApplicationPromptAssembler assembler = mock(ApplicationPromptAssembler.class);
    private final DocumentReviewer reviewer = mock(DocumentReviewer.class);
    private final GeneratedDocumentFinalizer finalizer = mock(GeneratedDocumentFinalizer.class);
    private final LetterGateLoop gateLoop = mock(LetterGateLoop.class);
    private final ApplicationDocumentService service = new ApplicationDocumentService(provider, new ObjectMapper(),
            assembler, reviewer, finalizer, mock(QualityScoreRecorder.class), gateLoop);

    private static GenerateDocumentCommand command(String type) {
        return new GenerateDocumentCommand(UUID.randomUUID(), type, null, POSTING, null, null, null, null,
                "Danish", false, null, "STANDARD", DEADLINE);
    }

    private void draftAnswers(String json) {
        PromptComposition prompt = new PromptComposition("s", "u", "", "", "", "", "u");
        when(assembler.assemble(any())).thenReturn(new GenerationInputs(prompt, null, "{}", POSTING));
        when(provider.generateJson(any(), anyString())).thenReturn(json);
        when(reviewer.autoReview(any(), anyString())).thenReturn("Gennemset.");
    }

    @Test
    void aLetterIsGatedAfterReviewAndSavedWithTheNeedsAndVerdict() throws Exception {
        // Bug: the needs vanish when the draft is parsed, the gate is skipped or runs on the
        // unreviewed draft, or the gated text and its review never reach the save.
        draftAnswers("{\"needs\":[\"" + String.join("\",\"", NEEDS) + "\"],\"body\":\"Udkast.\"}");
        LetterReview review = LetterReview.of(NEEDS,
                new LetterGateResult(LetterGateVerdict.PASSED, List.of(), 1, null), "Godkendt.");
        when(gateLoop.run(anyString(), any(), any(), any()))
                .thenReturn(new LetterGateLoop.Outcome("Godkendt.", review));

        service.generateDocument(command("COVER_LETTER")).get();

        ArgumentCaptor<DocumentReviewer.ReviewContext> reviewed = ArgumentCaptor.forClass(DocumentReviewer.ReviewContext.class);
        verify(reviewer).autoReview(reviewed.capture(), eq("Udkast."));
        assertThat(reviewed.getValue().needs()).isEqualTo(NEEDS);
        verify(gateLoop).run("Gennemset.", NEEDS, new LetterBrief("COVER_LETTER", POSTING, "Danish", null), DEADLINE);
        verify(finalizer).finalizeDocument(any(), any(), eq("Godkendt."), eq(review), any());
    }

    @Test
    void aRecruiterMessageIsNeitherGatedNorGivenAReview() throws Exception {
        // Bug: the one-page letter gate runs on a three-line message and rewrites it into a letter.
        draftAnswers("{\"body\":\"Hej.\"}");

        service.generateDocument(command("RECRUITER_MESSAGE")).get();

        verify(gateLoop, never()).run(anyString(), any(), any(), any());
        verify(finalizer).finalizeDocument(any(), any(), eq("Gennemset."), eq(null), any());
    }

    @Test
    void neverMoreThanFiveNeedsAndNoBlankOnes() throws Exception {
        // Bug: a model that lists eight needs gets an eight-paragraph letter past the one-page rule.
        draftAnswers("{\"needs\":[\"a\",\" \",\"b\",\"c\",\"d\",\"e\",\"f\"],\"body\":\"Udkast.\"}");
        when(gateLoop.run(anyString(), any(), any(), any())).thenReturn(new LetterGateLoop.Outcome("x", null));

        service.generateDocument(command("COVER_LETTER")).get();

        verify(gateLoop).run(anyString(), eq(List.of("a", "b", "c", "d", "e")), any(), any());
    }

    @Test
    void anUnsolicitedLetterKeepsItsNeedsWithoutAVerdict() throws Exception {
        // Bug: a letter with no posting skips the gate and its needs are dropped with it, so the
        // user never sees what the letter was written from.
        PromptComposition prompt = new PromptComposition("s", "u", "", "", "", "", "u");
        when(assembler.assemble(any())).thenReturn(new GenerationInputs(prompt, null, "{}", null));
        when(provider.generateJson(any(), anyString()))
                .thenReturn("{\"needs\":[\"" + String.join("\",\"", NEEDS) + "\"],\"body\":\"Udkast.\"}");
        when(reviewer.autoReview(any(), anyString())).thenReturn("Gennemset.");

        service.generateDocument(command("UNSOLICITED_APPLICATION")).get();

        verify(gateLoop, never()).run(anyString(), any(), any(), any());
        ArgumentCaptor<LetterReview> stored = ArgumentCaptor.forClass(LetterReview.class);
        verify(finalizer).finalizeDocument(any(), any(), eq("Gennemset."), stored.capture(), any());
        assertThat(stored.getValue().needs()).isEqualTo(NEEDS);
        assertThat(stored.getValue().gate()).isNull();
    }
}
