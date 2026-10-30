package com.autoapplicant.usecase.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.port.out.document.WritingProfileRepositoryPort;
import com.autoapplicant.usecase.document.DocumentReviewer.ReviewContext;
import com.autoapplicant.usecase.document.DocumentReviewer.ReviewOutcome;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** The reviewer's response parsing and the auto-review loop, with the AI provider scripted. */
class DocumentReviewerTest {

    private static final ReviewContext CTX = new ReviewContext(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "COVER_LETTER", "We need a Java developer.", "English", "DK");

    private final ChatProviderPort ai = mock(ChatProviderPort.class);
    private final DocumentReviewer reviewer = new DocumentReviewer(ai,
            mock(WritingProfileRepositoryPort.class), new PromptCompositionBuilder(),
            new ClicheGuard(), new ObjectMapper());

    @BeforeEach
    void enabled() {
        ReflectionTestUtils.setField(reviewer, "autoReviewEnabled", true);
        ReflectionTestUtils.setField(reviewer, "autoReviewMaxIterations", 3);
    }

    @Test
    void reviewReturnsTheRevisionAndEveryCritiquePoint() {
        respond(reply("v1", "tightened the opening", "added Kafka"));

        ReviewOutcome outcome = reviewer.review(CTX, "draft");

        assertThat(outcome.revised()).isEqualTo("v1");
        assertThat(outcome.critique()).containsExactly("tightened the opening", "added Kafka");
    }

    @Test
    void aBlankRevisionIsAFailedReview() {
        respond("{\"revisedContent\":\"  \",\"critique\":[\"x\"]}");

        assertThatThrownBy(() -> reviewer.review(CTX, "draft"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no revised content");
    }

    @Test
    void disabledAutoReviewReturnsTheDraftUntouched() {
        ReflectionTestUtils.setField(reviewer, "autoReviewEnabled", false);
        respond(reply("v1", "changed"));

        assertThat(reviewer.autoReview(CTX, "draft")).isEqualTo("draft");
    }

    @Test
    void autoReviewStopsAtTheIterationCap() {
        ReflectionTestUtils.setField(reviewer, "autoReviewMaxIterations", 2);
        respond(reply("v1", "changed"), reply("v2", "changed"), reply("v3", "changed"));

        assertThat(reviewer.autoReview(CTX, "draft")).isEqualTo("v2");
    }

    @Test
    void autoReviewStopsOnceAPassHasNothingLeftToCritique() {
        respond(reply("v1"), reply("v2", "changed"), reply("v3", "changed"));

        assertThat(reviewer.autoReview(CTX, "draft")).isEqualTo("v1");
    }

    @Test
    void aFailedPassKeepsTheBestDraftSoFarAndEndsTheLoop() {
        respond(reply("v1", "changed"), "not json at all", reply("v3", "changed"));

        assertThat(reviewer.autoReview(CTX, "draft")).isEqualTo("v1");
    }

    private void respond(String first, String... rest) {
        when(ai.generateJson(any(PromptComposition.class), anyString())).thenReturn(first, rest);
    }

    private static String reply(String revised, String... critique) {
        String points = critique.length == 0 ? "" : "\"" + String.join("\",\"", List.of(critique)) + "\"";
        return "{\"revisedContent\":\"" + revised + "\",\"critique\":[" + points + "]}";
    }
}
