package com.autoapplicant.usecase.ai;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.ai.GenerateDocumentCommand;
import com.autoapplicant.domain.ai.RefineDocumentRequest;
import com.autoapplicant.domain.ai.ReviewDocumentRequest;
import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.usecase.document.ApplicationDocumentAssembler;
import com.autoapplicant.usecase.document.CareerProfileContextService;
import com.autoapplicant.usecase.document.CvSectionTemplates;
import com.autoapplicant.usecase.document.DocumentReviewer;
import com.autoapplicant.usecase.document.GeneratedContentGuards;
import com.autoapplicant.usecase.document.GeneratedDocumentWriter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

/**
 * Every path that hands the user text they will send must run the content guards on that text, and
 * a blocking guard must stop it reaching the user. Refinement and review were once added without
 * the guards, which made "make it stronger" an unchecked invitation to invent on the path where the
 * user is least likely to re-read the output. The tailored-CV path is covered in
 * {@code TailoredCvAiPrivacyTest}, which already builds that service end to end.
 */
class GuardedPathsTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String PROFILE_JSON = "{\"profile\":true}";
    private static final String PRODUCED = "The text the model produced.";

    private final GeneratedContentGuards guards = mock(GeneratedContentGuards.class);
    private final CareerProfileContextService careerProfile = mock(CareerProfileContextService.class);
    private final ChatProviderPort ai = mock(ChatProviderPort.class);
    private final IllegalStateException blocked = new IllegalStateException("generation blocked");

    @Test
    void aBlockingGuardStopsARefinement() {
        when(careerProfile.buildJson(USER)).thenReturn(PROFILE_JSON);
        when(ai.generate(any(PromptComposition.class), anyString())).thenReturn(PRODUCED);
        blockOn(PRODUCED);
        DocumentRefinementService service = new DocumentRefinementService(
                ai, careerProfile, guards, mock(CvSectionTemplates.class));

        CompletableFuture<?> result = service.refine(new RefineDocumentRequest(
                USER, "the draft", "make it stronger", "the posting", "English", null, null));

        assertBlockedByTheGuard(result);
    }

    @Test
    void aBlockingGuardStopsAReview() {
        when(careerProfile.buildJson(USER)).thenReturn(PROFILE_JSON);
        DocumentReviewer reviewer = mock(DocumentReviewer.class);
        when(reviewer.review(any(DocumentReviewer.ReviewContext.class), eq("the draft")))
                .thenReturn(new DocumentReviewer.ReviewOutcome(PRODUCED, List.of("tightened")));
        blockOn(PRODUCED);
        DocumentReviewService service = new DocumentReviewService(reviewer, guards, careerProfile, ai);

        CompletableFuture<?> result = service.review(new ReviewDocumentRequest(
                USER, "the draft", "COVER_LETTER", "the posting", "English"));

        assertBlockedByTheGuard(result);
    }

    @Test
    void aBlockingGuardStopsAGeneratedDocumentBeforeItIsSaved() {
        blockOn(PRODUCED);
        GeneratedDocumentWriter writer = mock(GeneratedDocumentWriter.class);
        GeneratedDocumentFinalizer finalizer = new GeneratedDocumentFinalizer(
                guards, mock(ApplicationDocumentAssembler.class), writer);
        GenerateDocumentCommand cmd = new GenerateDocumentCommand(USER, "COVER_LETTER", null,
                "the posting", null, null, null, null, "English", false, null, null);
        GenerationInputs inputs = new GenerationInputs(null, null, PROFILE_JSON, "the posting");

        assertThatThrownBy(() -> finalizer.finalizeDocument(cmd, inputs, PRODUCED, "model"))
                .isSameAs(blocked);
        verifyNoInteractions(writer);
    }

    /** Blocks only on the produced text checked against the profile, so guarding anything else passes. */
    private void blockOn(String text) {
        when(guards.verify(eq(USER), eq(text), eq(PROFILE_JSON), anyString()))
                .thenThrow(blocked);
    }

    /**
     * Both services turn any exception into a failed future, so failing alone would also pass
     * if the guard call were dropped and something else threw; the cause must be the guard's
     * own block.
     */
    private void assertBlockedByTheGuard(CompletableFuture<?> result) {
        assertThatThrownBy(result::join)
                .isInstanceOf(CompletionException.class)
                .cause()
                .isSameAs(blocked);
    }
}
