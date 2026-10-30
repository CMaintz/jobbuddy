package com.autoapplicant.usecase.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.matching.MatchLabel;
import com.autoapplicant.domain.matching.MatchResult;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.domain.user.ProfileEmbedding;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The recommendation pipeline: retrieval, exclusion, ranking and the result shape. */
class MatchingServiceTest extends MatchingServiceFixture {

    @Test
    void returns_empty_list_when_profile_not_found() {
        when(profileRepo.findByUserId(userId)).thenReturn(Optional.empty());

        List<MatchResult> results = service.getRecommendations(userId, 10);
        assertThat(results).isEmpty();
        verifyNoInteractions(aiProvider, embeddingRepo, jobRepo);
    }

    @Test
    void returns_empty_list_when_profile_text_is_blank() {
        Profile blankProfile = profile("", "", List.of());
        when(profileRepo.findByUserId(userId)).thenReturn(Optional.of(blankProfile));

        List<MatchResult> results = service.getRecommendations(userId, 10);
        assertThat(results).isEmpty();
        verifyNoInteractions(aiProvider, embeddingRepo);
    }

    @Test
    void a_computed_fallback_embedding_is_cached_for_the_next_load() {
        Profile p = profileWithSkills("Java", "Spring");
        when(profileRepo.findByUserId(userId)).thenReturn(Optional.of(p));
        float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
        when(aiProvider.embed(anyString())).thenReturn(embedding);
        when(jobRepo.findByIds(any())).thenReturn(List.of());

        service.getRecommendations(userId, 10);

        verify(profileEmbeddingRepo).save(argThat(e -> e.userId().equals(userId)
                && e.embedding() == embedding));
    }

    @Test
    void a_cached_embedding_drives_retrieval_without_a_provider_call() {
        Profile p = profileWithSkills("Java");
        UUID jobId = UUID.randomUUID();
        float[] cached = new float[]{0.1f, 0.2f, 0.3f};
        when(profileRepo.findByUserId(userId)).thenReturn(Optional.of(p));
        when(profileEmbeddingRepo.findByUserId(userId)).thenReturn(Optional.of(
                new ProfileEmbedding(UUID.randomUUID(), userId, cached, "m", Instant.now())));
        when(embeddingRepo.findNearestNeighborJobIds(same(cached), anyInt()))
                .thenReturn(List.of(jobId));
        when(jobRepo.findByIds(any())).thenReturn(List.of(minimalJob(jobId)));

        List<MatchResult> results = service.getRecommendations(userId, 10);

        assertThat(results).extracting(MatchResult::jobId).containsExactly(jobId);
        verify(aiProvider, never()).embed(anyString());
        verify(profileEmbeddingRepo, never()).save(any());
    }

    @Test
    void a_recommendation_carries_the_job_the_user_and_a_labelled_score() {
        UUID jobId = UUID.randomUUID();
        stubFeed(profileWithSkills("Java"), List.of(minimalJob(jobId)));

        MatchResult result = service.getRecommendations(userId, 10).get(0);

        assertThat(result.jobId()).isEqualTo(jobId);
        assertThat(result.job().id()).isEqualTo(jobId);
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.totalScore()).isEqualTo(25);
        assertThat(result.matchLabel()).isEqualTo(MatchLabel.WEAK);
    }

    @Test
    void returns_the_highest_scoring_jobs_up_to_the_limit_best_first() {
        UUID best = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        UUID worst = UUID.randomUUID();
        UUID fifth = UUID.randomUUID();
        // Retrieval order is deliberately not score order, so the ranking has to come from the
        // sort. Overlap with the profile's one skill sets each score: 1/1, 1/2, 1/3, 1/4, 0.
        List<Job> feed = List.of(
                untiered(third, "Java", "Go", "Rust"),
                untiered(worst, "COBOL"),
                untiered(best, "Java"),
                untiered(second, "Java", "Go"),
                untiered(fifth, "Java", "Go", "Rust", "C"));
        stubFeed(profileWithSkills("Java"), feed);

        List<MatchResult> results = service.getRecommendations(userId, 2);

        assertThat(results).extracting(MatchResult::jobId).containsExactly(best, second);
        assertThat(results).extracting(MatchResult::totalScore).containsExactly(80, 53);
    }

    @Test
    void returns_empty_list_when_ai_provider_throws() {
        Profile profile = profileWithSkills("Java");
        when(profileRepo.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(aiProvider.embed(anyString())).thenThrow(new RuntimeException("OpenAI unavailable"));

        List<MatchResult> results = service.getRecommendations(userId, 10);
        assertThat(results).isEmpty();
    }

    @Test
    void skips_jobs_that_cannot_be_found_by_id() {
        UUID existingJobId = UUID.randomUUID();
        UUID missingJobId = UUID.randomUUID();
        Profile profile = profileWithSkills("Java");
        when(profileRepo.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(aiProvider.embed(anyString())).thenReturn(new float[]{0.5f});
        when(embeddingRepo.findNearestNeighborJobIds(any(), anyInt()))
                .thenReturn(List.of(existingJobId, missingJobId));
        when(jobRepo.findByIds(any())).thenReturn(List.of(minimalJob(existingJobId)));

        List<MatchResult> results = service.getRecommendations(userId, 10);

        assertThat(results).extracting(MatchResult::jobId).containsExactly(existingJobId);
    }

    @Test
    void a_commute_radius_widens_the_candidate_pool_so_the_filter_does_not_starve_the_feed() {
        Profile profile = profileWithSkills("Java");
        when(profileRepo.findByUserId(userId)).thenReturn(Optional.of(profile));
        stubPrefs(prefs().municipalities("København").maxCommuteKm(30).build());
        when(aiProvider.embed(anyString())).thenReturn(new float[]{0.5f});
        when(embeddingRepo.findNearestNeighborJobIds(any(), anyInt())).thenReturn(List.of());
        when(jobRepo.findByIds(any())).thenReturn(List.of());

        service.getRecommendations(userId, 10);

        // 10 * FILTERED_CANDIDATE_MULTIPLIER rather than 10 * CANDIDATE_MULTIPLIER
        verify(embeddingRepo).findNearestNeighborJobIds(any(), eq(150));
    }

    @Test
    void a_job_you_have_already_applied_to_is_not_recommended_again() {
        UUID appliedId = UUID.randomUUID();
        UUID freshId = UUID.randomUUID();
        when(applicationRepo.findAppliedJobIds(userId)).thenReturn(Set.of(appliedId));
        stubFeed(profileWithSkills("Java"), List.of(minimalJob(appliedId), minimalJob(freshId)));

        List<MatchResult> results = service.getRecommendations(userId, 10);

        assertThat(results).extracting(MatchResult::jobId).containsExactly(freshId);
    }

    private static Job untiered(UUID id, String... technologies) {
        return minimalJob(id).toBuilder().technologies(List.of(technologies)).build();
    }
}
