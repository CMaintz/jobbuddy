package com.autoapplicant.usecase.ai;

import static com.autoapplicant.usecase.ai.PostingAwareGuardsFixture.INVENTING_LETTER;
import static com.autoapplicant.usecase.ai.PostingAwareGuardsFixture.POSTING;
import static com.autoapplicant.usecase.ai.PostingAwareGuardsFixture.QUOTING_LETTER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.ai.ReviewDocumentRequest;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.usecase.document.CareerProfileContextService;
import com.autoapplicant.usecase.document.DocumentReviewer;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

/** The reviewer's rewrite is checked against the posting the letter answers, in strict mode. */
class DocumentReviewServicePostingTest {

    private final DocumentReviewer reviewer = mock(DocumentReviewer.class);
    private final CareerProfileContextService profile = mock(CareerProfileContextService.class);
    private final DocumentReviewService service = new DocumentReviewService(
            reviewer, PostingAwareGuardsFixture.guards("block"), profile, mock(ChatProviderPort.class));

    @Test
    void aPostingFigureQuotedAboutTheEmployerDoesNotBlockTheReview() throws Exception {
        reviewerReturns(QUOTING_LETTER);
        assertThat(service.review(request()).get().revisedContent()).isEqualTo(QUOTING_LETTER);
    }

    @Test
    void anInventedFigureStillBlocksTheReview() {
        reviewerReturns(INVENTING_LETTER);
        assertThatThrownBy(() -> service.review(request()).get())
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining("40 medarbejdere");
    }

    private void reviewerReturns(String revised) {
        when(profile.buildJson(any())).thenReturn("{}");
        when(reviewer.review(any(), anyString())).thenReturn(new DocumentReviewer.ReviewOutcome(revised, List.of()));
    }

    private static ReviewDocumentRequest request() {
        return new ReviewDocumentRequest(UUID.randomUUID(), "Udkast.", "COVER_LETTER", POSTING, "Danish");
    }
}
