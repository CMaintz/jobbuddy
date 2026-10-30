package com.autoapplicant.usecase.matching;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.JobEmbedding;
import com.autoapplicant.domain.matching.FeedbackType;
import com.autoapplicant.domain.matching.MatchResult;
import com.autoapplicant.domain.matching.RecommendationFeedback;
import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.domain.user.UserPreferences;
import com.autoapplicant.port.out.ai.AiProviderPort;
import com.autoapplicant.port.out.application.ApplicationRepositoryPort;
import com.autoapplicant.port.out.job.IgnoredJobRepositoryPort;
import com.autoapplicant.port.out.job.JobEmbeddingRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.matching.RecommendationFeedbackRepositoryPort;
import com.autoapplicant.port.out.skills.ProfileSkillRepositoryPort;
import com.autoapplicant.port.out.skills.SkillTaxonomyRepositoryPort;
import com.autoapplicant.port.out.user.PreferencesRepositoryPort;
import com.autoapplicant.port.out.user.ProfileEmbeddingRepositoryPort;
import com.autoapplicant.port.out.user.ProfileRepositoryPort;
import com.autoapplicant.usecase.skills.SkillCanonicalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Shared mocks and builders for the MatchingService tests, which are split by concern.
 *
 * <p>Every score the subclasses assert starts from a 25-point base: {@link #minimalJob} lists no
 * skills, so it earns no skill fit, and a profile holding only "Java" earns nothing against it.
 */
@ExtendWith(MockitoExtension.class)
abstract class MatchingServiceFixture {

    @Mock JobEmbeddingRepositoryPort embeddingRepo;
    @Mock JobRepositoryPort jobRepo;
    @Mock ProfileRepositoryPort profileRepo;
    @Mock PreferencesRepositoryPort prefsRepo;
    @Mock AiProviderPort aiProvider;
    @Mock IgnoredJobRepositoryPort ignoredJobRepo;
    @Mock RecommendationFeedbackRepositoryPort feedbackRepo;
    @Mock ProfileEmbeddingRepositoryPort profileEmbeddingRepo;
    @Mock ProfileSkillRepositoryPort profileSkillRepo;
    @Mock ApplicationRepositoryPort applicationRepo;
    @Mock SkillTaxonomyRepositoryPort taxonomyRepo;

    MatchingService service;
    final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUpService() {
        // The taxonomy is empty unless a test stubs it, so canonicalisation is plain
        // normalisation and "Java" matches only "java".
        service = new MatchingService(embeddingRepo, jobRepo, profileRepo, prefsRepo,
                aiProvider, ignoredJobRepo, feedbackRepo, profileEmbeddingRepo, profileSkillRepo,
                applicationRepo, new SkillCanonicalizer(taxonomyRepo));
    }

    /** A profile whose skills are stored as plain rows with no recorded proficiency. */
    Profile profile(String headline, String summary, List<String> skills) {
        if (!skills.isEmpty()) {
            lenient().when(profileSkillRepo.findByUserId(userId)).thenReturn(
                    skills.stream().map(name -> new ProfileSkill(UUID.randomUUID(), userId, name,
                            null, null, null, false, 0, null)).toList());
        }
        return new Profile(UUID.randomUUID(), userId, headline, summary,
                null, List.of(), List.of(),
                null, null, null, null, null,
                null, null);
    }

    Profile profileWithSkills(String... skills) {
        return profile("Dev", "Java", List.of(skills));
    }

    static Job minimalJob(UUID id) {
        return Job.builder()
                .id(id)
                .url("https://example.com")
                .title("Software Engineer")
                .companyName("Acme Corp")
                .location("Copenhagen")
                .build();
    }

    /** A job that states its asks in tiers, the post-V069 shape. */
    static Job tieredJob(UUID id, List<String> required, List<String> preferred) {
        List<String> all = new ArrayList<>(required);
        all.addAll(preferred);
        return minimalJob(id).toBuilder()
                .technologies(all)
                .requiredSkills(required)
                .preferredSkills(preferred)
                .build();
    }

    void stubFeed(Profile p, List<Job> jobs) {
        when(profileRepo.findByUserId(userId)).thenReturn(Optional.of(p));
        when(aiProvider.embed(anyString())).thenReturn(new float[]{0.5f});
        when(embeddingRepo.findNearestNeighborJobIds(any(), anyInt()))
                .thenReturn(jobs.stream().map(Job::id).toList());
        when(jobRepo.findByIds(any())).thenReturn(jobs);
    }

    void stubPrefs(UserPreferences prefs) {
        when(prefsRepo.findByUserId(userId)).thenReturn(Optional.of(prefs));
    }

    void stubFeedback(RecommendationFeedback... feedback) {
        when(feedbackRepo.findByUserId(userId)).thenReturn(List.of(feedback));
    }

    /** Gives a steered job an embedding, so its neighbours can be looked up. */
    void stubSteerEmbedding(UUID steeredJobId) {
        when(embeddingRepo.findByJobId(steeredJobId)).thenReturn(Optional.of(
                new JobEmbedding(UUID.randomUUID(), steeredJobId, new float[]{0.9f}, null, null)));
    }

    RecommendationFeedback feedback(UUID jobId, FeedbackType type) {
        return new RecommendationFeedback(UUID.randomUUID(), userId, jobId, type, Instant.now());
    }

    TestPreferences prefs() {
        return new TestPreferences(userId);
    }

    static MatchResult resultFor(List<MatchResult> results, UUID jobId) {
        return results.stream().filter(r -> r.jobId().equals(jobId)).findFirst().orElseThrow();
    }

    static int scoreOf(List<MatchResult> results, UUID jobId) {
        return resultFor(results, jobId).totalScore();
    }

    static List<String> reasonsFor(List<MatchResult> results, UUID jobId) {
        return resultFor(results, jobId).matchReasons();
    }
}
