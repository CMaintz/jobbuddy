package com.autoapplicant.usecase.matching;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.matching.MatchLabel;
import com.autoapplicant.domain.matching.MatchResult;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.domain.user.UserPreferences;
import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.port.out.ai.AiProviderPort;
import com.autoapplicant.port.out.job.JobEmbeddingRepositoryPort;
import com.autoapplicant.port.out.job.IgnoredJobRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.matching.RecommendationFeedbackRepositoryPort;
import com.autoapplicant.port.out.user.PreferencesRepositoryPort;
import com.autoapplicant.port.out.user.ProfileEmbeddingRepositoryPort;
import com.autoapplicant.port.out.user.ProfileRepositoryPort;
import com.autoapplicant.port.out.skills.ProfileSkillRepositoryPort;
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
    @Mock PreferencesRepositoryPort  prefsRepo;
    @Mock AiProviderPort             aiProvider;
    @Mock IgnoredJobRepositoryPort   ignoredJobRepo;
    @Mock RecommendationFeedbackRepositoryPort feedbackRepo;
    @Mock ProfileEmbeddingRepositoryPort profileEmbeddingRepo;
    @Mock ProfileSkillRepositoryPort profileSkillRepo;

    MatchingService service;
    UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new MatchingService(embeddingRepo, jobRepo, profileRepo, prefsRepo,
                aiProvider, ignoredJobRepo, feedbackRepo, profileEmbeddingRepo, profileSkillRepo);
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
        when(embeddingRepo.findNearestNeighborJobIds(embedding, 50)).thenReturn(List.of());
        when(jobRepo.findByIds(any())).thenReturn(List.of());

        service.getRecommendations(userId, 10);

        verify(aiProvider).embed(anyString());
        verify(embeddingRepo).findNearestNeighborJobIds(embedding, 50);
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
        when(embeddingRepo.findNearestNeighborJobIds(any(), eq(50))).thenReturn(List.of(jobId1, jobId2));
        when(jobRepo.findByIds(any())).thenReturn(List.of(job1, job2));

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
        when(jobRepo.findByIds(any())).thenReturn(List.of(minimalJob(jobId)));

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
        when(jobRepo.findByIds(any())).thenReturn(List.of(minimalJob(jobId)));

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
        // limit=2 → service fetches limit*5=10 neighbors
        when(embeddingRepo.findNearestNeighborJobIds(any(), eq(10))).thenReturn(List.of(id1, id2));
        when(jobRepo.findByIds(any())).thenReturn(List.of(minimalJob(id1), minimalJob(id2)));

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
        // Batch fetch returns only the existing job
        when(jobRepo.findByIds(any())).thenReturn(List.of(minimalJob(existingJobId)));

        List<MatchResult> results = service.getRecommendations(userId, 10);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).jobId()).isEqualTo(existingJobId);
    }

    // ── requirement tiers ─────────────────────────────────────────────────────
    //
    // The point of the split: a must-have you lack must cost you, and a nice-to-have you hold
    // must be worth less than a must-have you hold. Before the split both were one flat list,
    // so a missing requirement scored exactly the same as a requirement that was never stated.

    @Test
    void job_whose_requirements_are_all_met_outranks_one_with_a_missing_requirement() {
        Profile p = profile(userId, "Dev", "Java", List.of(), List.of("Java", "Spring"));
        UUID metId = UUID.randomUUID();
        UUID unmetId = UUID.randomUUID();
        Job met   = tieredJob(metId,   List.of("Java", "Spring"), List.of());
        Job unmet = tieredJob(unmetId, List.of("Java", "Kubernetes"), List.of());

        stubFeed(p, List.of(met, unmet));
        List<MatchResult> results = service.getRecommendations(userId, 10);

        assertThat(scoreOf(results, metId)).isGreaterThan(scoreOf(results, unmetId));
    }

    @Test
    void a_missing_requirement_is_named_in_the_match_reasons() {
        Profile p = profile(userId, "Dev", "Java", List.of(), List.of("Java"));
        UUID jobId = UUID.randomUUID();
        stubFeed(p, List.of(tieredJob(jobId, List.of("Java", "Kubernetes"), List.of())));

        List<MatchResult> results = service.getRecommendations(userId, 10);

        assertThat(results.get(0).matchReasons())
                .anyMatch(r -> r.contains("Missing required") && r.contains("Kubernetes"));
        assertThat(results.get(0).matchReasons()).contains("1/2 required skills covered");
    }

    @Test
    void holding_a_nice_to_have_raises_the_score_but_by_less_than_a_requirement() {
        UUID reqId  = UUID.randomUUID();
        UUID niceId = UUID.randomUUID();
        UUID noneId = UUID.randomUUID();
        Profile p = profile(userId, "Dev", "Java", List.of(), List.of("Terraform"));

        // Same single ask, differing only in the tier the posting put it in — plus a control
        // posting that asks for it and does not get it.
        stubFeed(p, List.of(tieredJob(reqId,  List.of("Terraform"), List.of()),
                            tieredJob(niceId, List.of(), List.of("Terraform")),
                            tieredJob(noneId, List.of("Kubernetes"), List.of())));

        List<MatchResult> results = service.getRecommendations(userId, 10);

        assertThat(scoreOf(results, reqId)).isGreaterThan(scoreOf(results, niceId));
        assertThat(scoreOf(results, niceId)).isGreaterThan(scoreOf(results, noneId));
    }

    @Test
    void a_job_enriched_before_the_tiers_existed_still_scores_on_flat_overlap() {
        Profile p = profile(userId, "Dev", "Java", List.of(), List.of("Java"));
        UUID overlapId = UUID.randomUUID();
        UUID unrelatedId = UUID.randomUUID();
        // Neither job has requiredSkills/preferredSkills — the pre-V069 shape.
        Job overlap = minimalJob(overlapId).toBuilder().technologies(List.of("Java")).build();
        Job unrelated = minimalJob(unrelatedId).toBuilder().technologies(List.of("COBOL")).build();

        stubFeed(p, List.of(overlap, unrelated));
        List<MatchResult> results = service.getRecommendations(userId, 10);

        assertThat(scoreOf(results, overlapId)).isGreaterThan(scoreOf(results, unrelatedId));
    }

    @Test
    void a_beginner_level_skill_covers_a_requirement_only_partially() {
        Profile p = profile(userId, "Dev", "Java", List.of(), List.of("Java"));
        UUID jobId = UUID.randomUUID();
        when(profileSkillRepo.findByUserId(userId)).thenReturn(List.of(
                new ProfileSkill(UUID.randomUUID(), userId, "Java", null,
                        "BEGINNER", null, false, 0, null)));
        stubFeed(p, List.of(tieredJob(jobId, List.of("Java"), List.of())));
        int beginnerScore = service.getRecommendations(userId, 10).get(0).totalScore();

        reset(profileSkillRepo, profileRepo, aiProvider, embeddingRepo, jobRepo, ignoredJobRepo, feedbackRepo, prefsRepo, profileEmbeddingRepo);
        when(profileSkillRepo.findByUserId(userId)).thenReturn(List.of(
                new ProfileSkill(UUID.randomUUID(), userId, "Java", null,
                        "EXPERT", null, true, 0, null)));
        stubFeed(p, List.of(tieredJob(jobId, List.of("Java"), List.of())));
        int expertScore = service.getRecommendations(userId, 10).get(0).totalScore();

        assertThat(expertScore).isGreaterThan(beginnerScore);
    }

    @Test
    void a_commute_radius_widens_the_candidate_pool_so_the_filter_does_not_starve_the_feed() {
        Profile p = profile(userId, "Dev", "Java", List.of(), List.of("Java"));
        when(profileRepo.findByUserId(userId)).thenReturn(Optional.of(p));
        when(prefsRepo.findByUserId(userId)).thenReturn(Optional.of(prefsWithRadius(30)));
        when(aiProvider.embed(anyString())).thenReturn(new float[]{0.5f});
        when(profileEmbeddingRepo.findByUserId(userId)).thenReturn(Optional.empty());
        when(embeddingRepo.findNearestNeighborJobIds(any(), anyInt())).thenReturn(List.of());
        when(jobRepo.findByIds(any())).thenReturn(List.of());

        service.getRecommendations(userId, 10);

        // 10 * FILTERED_CANDIDATE_MULTIPLIER rather than 10 * CANDIDATE_MULTIPLIER
        verify(embeddingRepo).findNearestNeighborJobIds(any(), eq(150));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Profile profile(UUID userId, String headline, String summary,
                            List<String> skills, List<String> technologies) {
        return new Profile(UUID.randomUUID(), userId, headline, summary,
                null, skills, technologies, List.of(), List.of(),
                null, null, null, null, null,
                null, null);
    }

    private Job minimalJob(UUID id) {
        return new Job(id, null, null, "https://example.com", "Software Engineer",
                null, "Acme Corp", null, null,
                null, null, null, "Copenhagen", null, null, null,
                null, null, null,
                List.of(), List.of(), List.of(),
                null, null, null, null, null, null, true,
                null, null, null, null, null, null, null,
                List.of(), List.of());
    }

    private void stubFeed(Profile p, List<Job> jobs) {
        when(profileRepo.findByUserId(userId)).thenReturn(Optional.of(p));
        when(aiProvider.embed(anyString())).thenReturn(new float[]{0.5f});
        when(embeddingRepo.findNearestNeighborJobIds(any(), anyInt()))
                .thenReturn(jobs.stream().map(Job::id).toList());
        when(jobRepo.findByIds(any())).thenReturn(jobs);
    }

    private static int scoreOf(List<MatchResult> results, UUID jobId) {
        return results.stream().filter(r -> r.jobId().equals(jobId)).findFirst().orElseThrow().totalScore();
    }

    /** A job that states its asks in tiers, the post-V069 shape. */
    private Job tieredJob(UUID id, List<String> required, List<String> preferred) {
        List<String> all = new java.util.ArrayList<>(required);
        all.addAll(preferred);
        return minimalJob(id).toBuilder()
                .technologies(all)
                .requiredSkills(required)
                .preferredSkills(preferred)
                .build();
    }

    private UserPreferences prefsWithRadius(int km) {
        return new UserPreferences(UUID.randomUUID(), userId, List.of(), List.of("K\u00f8benhavn"),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                null, null, km, false, null, null, null, null);
    }
}
