package com.autoapplicant.usecase.matching;

import com.autoapplicant.domain.job.JobEmbedding;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.matching.FeedbackType;
import com.autoapplicant.domain.matching.RecommendationFeedback;
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
import com.autoapplicant.port.out.application.ApplicationRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    @Mock ApplicationRepositoryPort applicationRepo;

    MatchingService service;
    UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new MatchingService(embeddingRepo, jobRepo, profileRepo, prefsRepo,
                aiProvider, ignoredJobRepo, feedbackRepo, profileEmbeddingRepo, profileSkillRepo,
                applicationRepo);
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
        Profile p = profile(userId, "Dev", "Java", List.of(), List.of());
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

    // ── already-applied exclusion ─────────────────────────────────────────────

    @Test
    void a_job_you_have_already_applied_to_is_not_recommended_again() {
        Profile p = profile(userId, "Dev", "Java", List.of(), List.of("Java"));
        UUID appliedId = UUID.randomUUID();
        UUID freshId = UUID.randomUUID();
        when(applicationRepo.findAppliedJobIds(userId)).thenReturn(Set.of(appliedId));
        stubFeed(p, List.of(minimalJob(appliedId), minimalJob(freshId)));

        List<MatchResult> results = service.getRecommendations(userId, 10);

        assertThat(results).extracting(MatchResult::jobId).containsExactly(freshId);
    }

    @Test
    void applying_is_a_separate_act_from_ignoring_and_neither_implies_the_other() {
        // Ignoring is a deliberate "not interested"; applying is the opposite. Both remove the job
        // from the feed, and one must not be inferred from the other.
        Profile p = profile(userId, "Dev", "Java", List.of(), List.of("Java"));
        UUID appliedId = UUID.randomUUID();
        when(applicationRepo.findAppliedJobIds(userId)).thenReturn(Set.of(appliedId));
        when(ignoredJobRepo.findJobIdsByUserId(userId)).thenReturn(Set.of());
        stubFeed(p, List.of(minimalJob(appliedId)));

        assertThat(service.getRecommendations(userId, 10)).isEmpty();
        verify(ignoredJobRepo, never()).save(any());
    }

    // ── item feedback vs steering ─────────────────────────────────────────────
    //
    // These are two different statements. A like is a verdict on one posting. "More like this" is
    // a request about a kind of posting, and is worth nothing unless it reaches postings the user
    // has not seen — which is exactly what was missing when all four values scored identically.

    @Test
    void more_like_this_raises_jobs_near_the_one_you_pointed_at() {
        Profile p = profile(userId, "Dev", "Java", List.of(), List.of("Java"));
        UUID steered = UUID.randomUUID();
        UUID neighbour = UUID.randomUUID();
        UUID unrelated = UUID.randomUUID();

        when(feedbackRepo.findByUserId(userId)).thenReturn(List.of(
                feedback(steered, FeedbackType.MORE_LIKE_THIS)));
        when(embeddingRepo.findByJobId(steered)).thenReturn(Optional.of(
                new JobEmbedding(UUID.randomUUID(), steered, new float[]{0.9f}, null, null)));
        stubFeed(p, List.of(minimalJob(neighbour), minimalJob(unrelated)));
        // Narrower than stubFeed's matcher and declared after it, so it serves the steer lookup
        // while the main retrieval (limit * 5) still gets the feed.
        when(embeddingRepo.findNearestNeighborJobIds(any(), eq(25)))
                .thenReturn(List.of(steered, neighbour));

        List<MatchResult> results = service.getRecommendations(userId, 10);

        assertThat(scoreOf(results, neighbour)).isGreaterThan(scoreOf(results, unrelated));
        assertThat(reasonsFor(results, neighbour))
                .anyMatch(r -> r.contains("asked for more of"));
    }

    @Test
    void fewer_like_this_lowers_jobs_near_the_one_you_pointed_at() {
        Profile p = profile(userId, "Dev", "Java", List.of(), List.of("Java"));
        UUID steered = UUID.randomUUID();
        UUID neighbour = UUID.randomUUID();
        UUID unrelated = UUID.randomUUID();

        when(feedbackRepo.findByUserId(userId)).thenReturn(List.of(
                feedback(steered, FeedbackType.FEWER_LIKE_THIS)));
        when(embeddingRepo.findByJobId(steered)).thenReturn(Optional.of(
                new JobEmbedding(UUID.randomUUID(), steered, new float[]{0.9f}, null, null)));
        stubFeed(p, List.of(minimalJob(neighbour), minimalJob(unrelated)));
        when(embeddingRepo.findNearestNeighborJobIds(any(), eq(25)))
                .thenReturn(List.of(steered, neighbour));

        List<MatchResult> results = service.getRecommendations(userId, 10);

        assertThat(scoreOf(results, neighbour)).isLessThan(scoreOf(results, unrelated));
    }

    @Test
    void a_plain_like_stays_a_verdict_on_that_one_posting() {
        // The distinction the enum has always claimed and never had: LIKE must not steer.
        Profile p = profile(userId, "Dev", "Java", List.of(), List.of("Java"));
        UUID liked = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        when(feedbackRepo.findByUserId(userId)).thenReturn(List.of(feedback(liked, FeedbackType.LIKE)));
        stubFeed(p, List.of(minimalJob(liked), minimalJob(other)));

        List<MatchResult> results = service.getRecommendations(userId, 10);

        assertThat(scoreOf(results, liked)).isGreaterThan(scoreOf(results, other));
        verify(embeddingRepo, never()).findByJobId(any());
    }

    @Test
    void what_you_said_about_a_job_outranks_what_was_inferred_from_its_neighbour() {
        Profile p = profile(userId, "Dev", "Java", List.of(), List.of("Java"));
        UUID steered = UUID.randomUUID();
        UUID judged = UUID.randomUUID();

        // "More of these" pulls in `judged` as a neighbour, but the user disliked `judged` outright.
        when(feedbackRepo.findByUserId(userId)).thenReturn(List.of(
                feedback(steered, FeedbackType.MORE_LIKE_THIS),
                feedback(judged, FeedbackType.DISLIKE)));
        when(embeddingRepo.findByJobId(steered)).thenReturn(Optional.of(
                new JobEmbedding(UUID.randomUUID(), steered, new float[]{0.9f}, null, null)));

        UUID plain = UUID.randomUUID();
        stubFeed(p, List.of(minimalJob(judged), minimalJob(plain)));
        when(embeddingRepo.findNearestNeighborJobIds(any(), eq(25))).thenReturn(List.of(judged));

        List<MatchResult> results = service.getRecommendations(userId, 10);

        assertThat(scoreOf(results, judged)).isLessThan(scoreOf(results, plain));
    }

    @Test
    void steering_costs_nothing_when_the_user_has_never_steered() {
        Profile p = profile(userId, "Dev", "Java", List.of(), List.of("Java"));
        when(feedbackRepo.findByUserId(userId)).thenReturn(List.of());
        stubFeed(p, List.of(minimalJob(UUID.randomUUID())));

        service.getRecommendations(userId, 10);

        verify(embeddingRepo, never()).findByJobId(any());
    }

    @Test
    void only_the_most_recent_steer_signals_are_honoured() {
        // Each signal costs a nearest-neighbour query per request; fifty steers must not mean
        // fifty queries.
        Profile p = profile(userId, "Dev", "Java", List.of(), List.of("Java"));
        List<RecommendationFeedback> many = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            many.add(new RecommendationFeedback(UUID.randomUUID(), userId, UUID.randomUUID(),
                    FeedbackType.MORE_LIKE_THIS, java.time.Instant.now().minusSeconds(i)));
        }
        when(feedbackRepo.findByUserId(userId)).thenReturn(many);
        when(embeddingRepo.findByJobId(any())).thenReturn(Optional.empty());
        stubFeed(p, List.of(minimalJob(UUID.randomUUID())));

        service.getRecommendations(userId, 10);

        verify(embeddingRepo, times(5)).findByJobId(any());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * A profile plus the skills that go with it. Skills live in profile_skills, so giving a
     * candidate a skill means stubbing that repository — the two arguments are kept apart only
     * because the old signature distinguished them; both become plain skill rows.
     */
    private Profile profile(UUID userId, String headline, String summary,
                            List<String> skills, List<String> technologies) {
        List<String> all = new java.util.ArrayList<>(skills);
        all.addAll(technologies);
        if (!all.isEmpty()) {
            lenient().when(profileSkillRepo.findByUserId(userId)).thenReturn(
                    all.stream().map(name -> new ProfileSkill(UUID.randomUUID(), userId, name,
                            null, null, null, false, 0, null)).toList());
        }
        return new Profile(UUID.randomUUID(), userId, headline, summary,
                null, List.of(), List.of(),
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

    private RecommendationFeedback feedback(UUID jobId, FeedbackType type) {
        return new RecommendationFeedback(UUID.randomUUID(), userId, jobId, type,
                java.time.Instant.now());
    }

    private static List<String> reasonsFor(List<MatchResult> results, UUID jobId) {
        return results.stream().filter(r -> r.jobId().equals(jobId)).findFirst().orElseThrow().matchReasons();
    }
}
