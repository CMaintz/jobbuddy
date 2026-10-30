package com.autoapplicant.usecase.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.matching.FeedbackType;
import com.autoapplicant.domain.matching.MatchResult;
import com.autoapplicant.domain.matching.RecommendationFeedback;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Item feedback versus steering. A like is a verdict on one posting. "More like this" is a request
 * about a kind of posting, and is worth nothing unless it reaches postings the user has not seen.
 */
class MatchingSteeringTest extends MatchingServiceFixture {

    /** The steer lookup asks for this many neighbours; the main retrieval asks for limit * 5. */
    private static final int STEER_NEIGHBOURS = 25;

    @Test
    void more_like_this_raises_jobs_near_the_one_you_pointed_at() {
        UUID steered = UUID.randomUUID();
        UUID neighbour = UUID.randomUUID();
        UUID unrelated = UUID.randomUUID();
        stubFeedback(feedback(steered, FeedbackType.MORE_LIKE_THIS));
        stubSteerEmbedding(steered);
        stubFeed(profileWithSkills("Java"), List.of(minimalJob(neighbour), minimalJob(unrelated)));
        // Narrower than stubFeed's matcher and declared after it, so it serves the steer lookup
        // while the main retrieval still gets the feed.
        when(embeddingRepo.findNearestNeighborJobIds(any(), eq(STEER_NEIGHBOURS)))
                .thenReturn(List.of(steered, neighbour));

        List<MatchResult> results = service.getRecommendations(userId, 10);

        assertThat(scoreOf(results, neighbour)).isGreaterThan(scoreOf(results, unrelated));
        assertThat(reasonsFor(results, neighbour)).anyMatch(r -> r.contains("asked for more of"));
    }

    @Test
    void fewer_like_this_lowers_jobs_near_the_one_you_pointed_at() {
        UUID steered = UUID.randomUUID();
        UUID neighbour = UUID.randomUUID();
        UUID unrelated = UUID.randomUUID();
        stubFeedback(feedback(steered, FeedbackType.FEWER_LIKE_THIS));
        stubSteerEmbedding(steered);
        stubFeed(profileWithSkills("Java"), List.of(minimalJob(neighbour), minimalJob(unrelated)));
        when(embeddingRepo.findNearestNeighborJobIds(any(), eq(STEER_NEIGHBOURS)))
                .thenReturn(List.of(steered, neighbour));

        List<MatchResult> results = service.getRecommendations(userId, 10);

        assertThat(scoreOf(results, neighbour)).isLessThan(scoreOf(results, unrelated));
    }

    @Test
    void a_plain_like_stays_a_verdict_on_that_one_posting() {
        // LIKE must not steer: it raises the liked posting and looks up no neighbours.
        UUID liked = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        stubFeedback(feedback(liked, FeedbackType.LIKE));
        stubFeed(profileWithSkills("Java"), List.of(minimalJob(liked), minimalJob(other)));

        List<MatchResult> results = service.getRecommendations(userId, 10);

        assertThat(scoreOf(results, liked)).isGreaterThan(scoreOf(results, other));
        verify(embeddingRepo, never()).findByJobId(any());
    }

    @Test
    void what_you_said_about_a_job_outranks_what_was_inferred_from_its_neighbour() {
        UUID steered = UUID.randomUUID();
        UUID judged = UUID.randomUUID();
        UUID plain = UUID.randomUUID();
        // "More of these" pulls in `judged` as a neighbour, but the user disliked `judged` outright.
        stubFeedback(feedback(steered, FeedbackType.MORE_LIKE_THIS),
                feedback(judged, FeedbackType.DISLIKE));
        stubSteerEmbedding(steered);
        stubFeed(profileWithSkills("Java"), List.of(minimalJob(judged), minimalJob(plain)));
        when(embeddingRepo.findNearestNeighborJobIds(any(), eq(STEER_NEIGHBOURS)))
                .thenReturn(List.of(judged));

        List<MatchResult> results = service.getRecommendations(userId, 10);

        assertThat(scoreOf(results, judged)).isLessThan(scoreOf(results, plain));
    }

    @Test
    void steering_toward_one_neighbour_twice_does_not_double_the_boost() {
        // Several steers toward similar postings must not stack past a direct like on their
        // shared neighbour: the inferred boost stays at 6 however many signals reach it.
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID neighbour = UUID.randomUUID();
        stubFeedback(feedback(first, FeedbackType.MORE_LIKE_THIS),
                feedback(second, FeedbackType.MORE_LIKE_THIS));
        stubSteerEmbedding(first);
        stubSteerEmbedding(second);
        stubFeed(profileWithSkills("Java"), List.of(minimalJob(neighbour)));
        when(embeddingRepo.findNearestNeighborJobIds(any(), eq(STEER_NEIGHBOURS)))
                .thenReturn(List.of(neighbour));

        List<MatchResult> results = service.getRecommendations(userId, 10);

        assertThat(scoreOf(results, neighbour)).isEqualTo(31);
    }

    @Test
    void only_the_most_recent_steer_signals_are_honoured() {
        // Each signal costs a nearest-neighbour query per request; twelve steers must not mean
        // twelve queries, and the five that are honoured must be the newest.
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        List<UUID> newestFirst = new ArrayList<>();
        List<RecommendationFeedback> oldestFirst = new ArrayList<>();
        for (int ageSeconds = 11; ageSeconds >= 0; ageSeconds--) {
            UUID jobId = UUID.randomUUID();
            newestFirst.add(0, jobId);
            oldestFirst.add(new RecommendationFeedback(UUID.randomUUID(), userId, jobId,
                    FeedbackType.MORE_LIKE_THIS, now.minusSeconds(ageSeconds)));
        }
        when(feedbackRepo.findByUserId(userId)).thenReturn(oldestFirst);
        when(embeddingRepo.findByJobId(any())).thenReturn(Optional.empty());
        stubFeed(profileWithSkills("Java"), List.of(minimalJob(UUID.randomUUID())));

        service.getRecommendations(userId, 10);

        ArgumentCaptor<UUID> looked = ArgumentCaptor.forClass(UUID.class);
        verify(embeddingRepo, atLeastOnce()).findByJobId(looked.capture());
        assertThat(looked.getAllValues())
                .containsExactlyInAnyOrderElementsOf(newestFirst.subList(0, 5));
    }
}
