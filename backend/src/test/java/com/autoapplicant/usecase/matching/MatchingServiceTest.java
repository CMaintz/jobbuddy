package com.autoapplicant.usecase.matching;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.matching.MatchLabel;
import com.autoapplicant.domain.matching.MatchResult;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.port.out.ai.AiProviderPort;
import com.autoapplicant.port.out.job.JobEmbeddingRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.user.ProfileRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchingServiceTest {

    @Mock JobEmbeddingRepositoryPort embeddingRepo;
    @Mock JobRepositoryPort          jobRepo;
    @Mock ProfileRepositoryPort      profileRepo;
    @Mock AiProviderPort             aiProvider;

    MatchingService service;
    UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new MatchingService(embeddingRepo, jobRepo, profileRepo, aiProvider);
    }

    // ── empty / missing profile ───────────────────────────────────────────────

    @Test
    void returns_empty_list_when_profile_not_found() {
        when(profileRepo.findByUserId(userId)).thenReturn(Optional.empty());

        List<MatchResult> results = service.getRecommendations(userId, 10);
        assertThat(results).isEmpty();
        verifyNoInteractions(aiProvider, embeddingRepo, jobRepo);
    }

    @Test
    void returns_empty_list_when_profile_text_is_blank() {
        // headline and summary are empty strings; skills/technologies are empty — concatenation is all spaces
        Profile blankProfile = profile(userId, "", "", List.of(), List.of());
        when(profileRepo.findByUserId(userId)).thenReturn(Optional.of(blankProfile));

        List<MatchResult> results = service.getRecommendations(userId, 10);
        assertThat(results).isEmpty();
        verifyNoInteractions(aiProvider, embeddingRepo);
    }

    // ── successful recommendation flow ────────────────────────────────────────

    @Test
    void embeds_profile_text_and_queries_nearest_neighbors() {
        Profile p = profile(userId, "Senior Java Engineer", "Spring Boot expert",
                List.of("Java"), List.of("Spring"));
        when(profileRepo.findByUserId(userId)).thenReturn(Optional.of(p));
        float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
        when(aiProvider.embed(anyString())).thenReturn(embedding);
        when(embeddingRepo.findNearestNeighborJobIds(embedding, 20)).thenReturn(List.of());

        service.getRecommendations(userId, 10);

        verify(aiProvider).embed(anyString());
        verify(embeddingRepo).findNearestNeighborJobIds(embedding, 20);
    }

    @Test
    void maps_nearest_neighbor_jobs_to_match_results() {
        Profile p = profile(userId, "Developer", "Java", List.of("Java"), List.of());
        UUID jobId1 = UUID.randomUUID();
        UUID jobId2 = UUID.randomUUID();
        Job job1 = minimalJob(jobId1);
        Job job2 = minimalJob(jobId2);

        when(profileRepo.findByUserId(userId)).thenReturn(Optional.of(p));
        when(aiProvider.embed(anyString())).thenReturn(new float[]{0.5f});
        when(embeddingRepo.findNearestNeighborJobIds(any(), eq(20))).thenReturn(List.of(jobId1, jobId2));
        when(jobRepo.findById(jobId1)).thenReturn(Optional.of(job1));
        when(jobRepo.findById(jobId2)).thenReturn(Optional.of(job2));

        List<MatchResult> results = service.getRecommendations(userId, 10);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(MatchResult::jobId).containsExactlyInAnyOrder(jobId1, jobId2);
    }

    @Test
    void match_results_have_correct_user_id() {
        Profile p = profile(userId, "Dev", "Java", List.of("Java"), List.of());
        UUID jobId = UUID.randomUUID();
        when(profileRepo.findByUserId(userId)).thenReturn(Optional.of(p));
        when(aiProvider.embed(anyString())).thenReturn(new float[]{0.5f});
        when(embeddingRepo.findNearestNeighborJobIds(any(), anyInt())).thenReturn(List.of(jobId));
        when(jobRepo.findById(jobId)).thenReturn(Optional.of(minimalJob(jobId)));

        List<MatchResult> results = service.getRecommendations(userId, 10);
        assertThat(results.get(0).userId()).isEqualTo(userId);
    }

    @Test
    void match_results_have_match_label_set() {
        Profile p = profile(userId, "Dev", "Java", List.of("Java"), List.of());
        UUID jobId = UUID.randomUUID();
        when(profileRepo.findByUserId(userId)).thenReturn(Optional.of(p));
        when(aiProvider.embed(anyString())).thenReturn(new float[]{0.5f});
        when(embeddingRepo.findNearestNeighborJobIds(any(), anyInt())).thenReturn(List.of(jobId));
        when(jobRepo.findById(jobId)).thenReturn(Optional.of(minimalJob(jobId)));

        List<MatchResult> results = service.getRecommendations(userId, 10);
        assertThat(results.get(0).matchLabel()).isNotNull();
    }

    @Test
    void respects_limit_parameter() {
        Profile p = profile(userId, "Dev", "Java", List.of("Java"), List.of());
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(profileRepo.findByUserId(userId)).thenReturn(Optional.of(p));
        when(aiProvider.embed(anyString())).thenReturn(new float[]{0.5f});
        // limit=2 → service fetches limit*2=4 neighbors; return 2 so no over-stubbing
        when(embeddingRepo.findNearestNeighborJobIds(any(), eq(4))).thenReturn(List.of(id1, id2));
        when(jobRepo.findById(id1)).thenReturn(Optional.of(minimalJob(id1)));
        when(jobRepo.findById(id2)).thenReturn(Optional.of(minimalJob(id2)));

        List<MatchResult> results = service.getRecommendations(userId, 2);
        assertThat(results).hasSizeLessThanOrEqualTo(2);
    }

    // ── exception resilience ──────────────────────────────────────────────────

    @Test
    void returns_empty_list_when_ai_provider_throws() {
        Profile p = profile(userId, "Dev", "Java", List.of("Java"), List.of());
        when(profileRepo.findByUserId(userId)).thenReturn(Optional.of(p));
        when(aiProvider.embed(anyString())).thenThrow(new RuntimeException("OpenAI unavailable"));

        List<MatchResult> results = service.getRecommendations(userId, 10);
        assertThat(results).isEmpty();
    }

    @Test
    void skips_jobs_that_cannot_be_found_by_id() {
        Profile p = profile(userId, "Dev", "Java", List.of("Java"), List.of());
        UUID existingJobId = UUID.randomUUID();
        UUID missingJobId  = UUID.randomUUID();

        when(profileRepo.findByUserId(userId)).thenReturn(Optional.of(p));
        when(aiProvider.embed(anyString())).thenReturn(new float[]{0.5f});
        when(embeddingRepo.findNearestNeighborJobIds(any(), anyInt()))
                .thenReturn(List.of(existingJobId, missingJobId));
        when(jobRepo.findById(existingJobId)).thenReturn(Optional.of(minimalJob(existingJobId)));
        when(jobRepo.findById(missingJobId)).thenReturn(Optional.empty());

        List<MatchResult> results = service.getRecommendations(userId, 10);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).jobId()).isEqualTo(existingJobId);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Profile profile(UUID userId, String headline, String summary,
                            List<String> skills, List<String> technologies) {
        return new Profile(UUID.randomUUID(), userId, "Test User", headline, summary,
                null, null, null, null, null, null, null, null,
                skills, technologies, List.of(),
                null, null, "DKK", null, null, null, null);
    }

    private Job minimalJob(UUID id) {
        return new Job(id, null, null, "https://example.com", "Software Engineer",
                null, "Acme Corp", null, null,
                null, null, null, "Copenhagen", null, null, null,
                null, null, null,
                List.of(), List.of(), List.of(),
                null, null, null, null, null, null, true,
                null, null);
    }
}
