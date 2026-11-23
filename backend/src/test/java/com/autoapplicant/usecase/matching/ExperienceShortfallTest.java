package com.autoapplicant.usecase.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.JobRequirement;
import com.autoapplicant.domain.job.RequirementKind;
import com.autoapplicant.domain.job.RequirementTier;
import com.autoapplicant.domain.matching.MatchResult;
import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.domain.user.WorkExperience;
import com.autoapplicant.port.out.ai.EmbeddingProviderPort;
import com.autoapplicant.port.out.application.ApplicationRepositoryPort;
import com.autoapplicant.port.out.job.IgnoredJobRepositoryPort;
import com.autoapplicant.port.out.job.JobEmbeddingRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.matching.RecommendationFeedbackRepositoryPort;
import com.autoapplicant.port.out.skills.ParsedSkillSuggestionRepositoryPort;
import com.autoapplicant.port.out.skills.ProfileSkillRepositoryPort;
import com.autoapplicant.port.out.skills.SkillTaxonomyRepositoryPort;
import com.autoapplicant.port.out.user.CertificationRepositoryPort;
import com.autoapplicant.port.out.user.EducationRepositoryPort;
import com.autoapplicant.port.out.user.PreferencesRepositoryPort;
import com.autoapplicant.port.out.user.ProfileEmbeddingRepositoryPort;
import com.autoapplicant.port.out.user.ProfileRepositoryPort;
import com.autoapplicant.port.out.user.WorkExperienceRepositoryPort;
import com.autoapplicant.usecase.skills.SkillCanonicalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * What a required experience ask the work history does not meet costs a posting that is still
 * within reach: less the closer the profile's skills come to the area, never so much that the
 * posting falls below one that is out of reach.
 */
class ExperienceShortfallTest {

    private static final String JUNIOR_ASK =
            "Erfaring med at definere krav til sikkerhedsarkitektur";
    private static final List<String> HELD_ASKS = List.of("NIS2", "ISO 27001", "Risikovurdering");
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);
    private static final int OUT_OF_REACH_SCORE = 25;

    private final UUID userId = UUID.randomUUID();
    private final Map<String, Double> similarities = new HashMap<>();
    private List<String> skills = HELD_ASKS;
    private List<WorkExperience> history = List.of(role("Kommunikationskonsulent",
            "Kampagner, presse og awareness om informationssikkerhed"));

    @Test
    void a_lacked_area_costs_the_full_penalty_when_nothing_in_the_profile_comes_close() {
        Job asking = posting(required(JUNIOR_ASK));
        Job plain = posting();

        Map<Job, MatchResult> results = feed(asking, plain);

        assertThat(score(results, asking))
                .isEqualTo(score(results, plain) - RequiredExperience.FAR_SHORTFALL_PENALTY);
        assertThat(results.get(asking).matchReasons()).first()
                .isEqualTo("Beder om erfaring med sikkerhedsarkitektur, som du ikke har endnu");
    }

    /**
     * The area is never one of the posting's own skill labels here, so this only passes when the
     * area itself is handed over to be compared by meaning.
     */
    @Test
    void a_lacked_area_costs_the_small_penalty_when_a_held_skill_is_close_in_meaning() {
        skills = List.of("NIS2", "ISO 27001", "Risikovurdering", "Informationssikkerhed");
        similarities.put("Sikkerhedsarkitektur|Informationssikkerhed", 0.80);
        Job asking = posting(required(JUNIOR_ASK));
        Job plain = posting();

        Map<Job, MatchResult> results = feed(asking, plain);

        assertThat(score(results, asking))
                .isEqualTo(score(results, plain) - RequiredExperience.NEAR_SHORTFALL_PENALTY);
        assertThat(results.get(asking).matchReasons()).first()
                .isEqualTo("Beder om erfaring med sikkerhedsarkitektur; du har noget beslægtet");
    }

    @Test
    void an_area_the_work_history_holds_costs_nothing() {
        history = List.of(role("Konsulent", "Definerede krav til sikkerhedsarkitektur"));
        Job asking = posting(required(JUNIOR_ASK));
        Job plain = posting();

        Map<Job, MatchResult> results = feed(asking, plain);

        assertThat(score(results, asking)).isEqualTo(score(results, plain));
    }

    @Test
    void an_ask_the_posting_only_prefers_costs_nothing() {
        Job asking = posting(new JobRequirement(JUNIOR_ASK, RequirementTier.PREFERRED,
                RequirementKind.EXPERIENCE, null));
        Job plain = posting();

        Map<Job, MatchResult> results = feed(asking, plain);

        assertThat(score(results, asking)).isEqualTo(score(results, plain));
    }

    @Test
    void an_ask_that_leaves_a_way_in_costs_nothing() {
        Job asking = posting(required("Gerne erfaring med sikkerhedsarkitektur"));
        Job plain = posting();

        Map<Job, MatchResult> results = feed(asking, plain);

        assertThat(score(results, asking)).isEqualTo(score(results, plain));
    }

    @Test
    void a_short_stint_in_the_area_costs_nothing() {
        history = List.of(new WorkExperience(UUID.randomUUID(), UUID.randomUUID(), "Firma",
                "Konsulent", null, "Penetrationstest af webapplikationer",
                LocalDate.of(2025, 12, 1), null, true, List.of(), List.of(), 0, null, null,
                List.of()));
        Job asking = posting(required("2 års erfaring med penetrationstest"));
        Job plain = posting();

        Map<Job, MatchResult> results = feed(asking, plain);

        assertThat(score(results, asking)).isEqualTo(score(results, plain));
    }

    @Test
    void the_penalty_slides_with_how_close_the_nearest_skill_comes() {
        skills = List.of("NIS2", "ISO 27001", "Risikovurdering", "Informationssikkerhed");
        // Two thirds of the way from where meaning starts to count to full closeness.
        similarities.put("Sikkerhedsarkitektur|Informationssikkerhed", 0.70);
        Job asking = posting(required(JUNIOR_ASK));
        Job plain = posting();

        Map<Job, MatchResult> results = feed(asking, plain);

        assertThat(score(results, asking)).isEqualTo(score(results, plain) - 7);
    }

    /** Either area would do, so the one the profile comes closest to decides the cost. */
    @Test
    void the_closest_of_the_alternatives_asked_counts() {
        skills = List.of("NIS2", "ISO 27001", "Risikovurdering", "Sårbarhedsscanning");
        similarities.put("Penetrationstest|Sårbarhedsscanning", 0.80);
        Job asking = posting(
                required("Erfaring med sikkerhedsarkitektur eller penetrationstest"));
        Job plain = posting();

        Map<Job, MatchResult> results = feed(asking, plain);

        assertThat(score(results, asking))
                .isEqualTo(score(results, plain) - RequiredExperience.NEAR_SHORTFALL_PENALTY);
    }

    /** The cap rules a senior ask; the penalty must not come on top of it. */
    @Test
    void a_senior_ask_still_puts_the_post_out_of_reach() {
        Job asking = posting(required("Mindst 5 års erfaring med sikkerhedsarkitektur"));

        MatchResult result = feed(asking).get(asking);

        assertThat(result.totalScore()).isEqualTo(OUT_OF_REACH_SCORE);
        assertThat(result.matchReasons()).first()
                .isEqualTo("Kræver 5 års erfaring med sikkerhedsarkitektur");
    }

    /** A post within reach must never rank below one that is out of reach. */
    @Test
    void the_penalty_never_takes_a_post_below_an_out_of_reach_one() {
        skills = List.of("Bogholderi");

        MatchResult result = feed(posting(required(JUNIOR_ASK))).values().iterator().next();

        assertThat(result.totalScore()).isEqualTo(OUT_OF_REACH_SCORE);
    }

    private Map<Job, MatchResult> feed(Job... jobs) {
        ProfileRepositoryPort profiles = mock(ProfileRepositoryPort.class);
        when(profiles.findByUserId(userId)).thenReturn(Optional.of(new Profile(
                UUID.randomUUID(), userId, "Headline", "", null, List.of(), List.of(), null, null,
                null, null, null, null, null)));
        ProfileSkillRepositoryPort profileSkills = mock(ProfileSkillRepositoryPort.class);
        when(profileSkills.findByUserId(userId)).thenReturn(skills.stream()
                .map(name -> new ProfileSkill(UUID.randomUUID(), userId, name, null, null, null,
                        false, 0, null))
                .toList());
        WorkExperienceRepositoryPort experience = mock(WorkExperienceRepositoryPort.class);
        when(experience.findByUserId(userId)).thenReturn(history);
        EmbeddingProviderPort ai = mock(EmbeddingProviderPort.class);
        when(ai.embed(anyString())).thenReturn(new float[] {1f});
        JobEmbeddingRepositoryPort embeddings = mock(JobEmbeddingRepositoryPort.class);
        when(embeddings.findNearestNeighborJobIds(any(), anyInt()))
                .thenReturn(List.of(jobs).stream().map(Job::id).toList());
        JobRepositoryPort jobRepo = mock(JobRepositoryPort.class);
        when(jobRepo.findByIds(any())).thenReturn(List.of(jobs));
        MatchingService service = new MatchingService(embeddings, jobRepo, profiles,
                mock(PreferencesRepositoryPort.class), ai, mock(IgnoredJobRepositoryPort.class),
                mock(RecommendationFeedbackRepositoryPort.class),
                mock(ProfileEmbeddingRepositoryPort.class), mock(ApplicationRepositoryPort.class),
                new MatchProfileLoader(
                        new SkillCanonicalizer(mock(SkillTaxonomyRepositoryPort.class)),
                        new MatchProfileLoader.Sources(profileSkills,
                                mock(ParsedSkillSuggestionRepositoryPort.class),
                                mock(EducationRepositoryPort.class),
                                mock(CertificationRepositoryPort.class), experience),
                        this::embeddedOnly,
                        Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC),
                                ZoneOffset.UTC)));
        Map<Job, MatchResult> byJob = new HashMap<>();
        List<MatchResult> results = service.getRecommendations(userId, 10);
        for (Job job : jobs) {
            byJob.put(job, results.stream().filter(r -> r.jobId().equals(job.id())).findFirst()
                    .orElseThrow());
        }
        return byJob;
    }

    /** As in production, a label never handed over for embedding has no similarity at all. */
    private SkillSimilarity embeddedOnly(Collection<String> labels) {
        Set<String> embedded = Set.copyOf(labels);
        return (ask, skill) -> embedded.contains(ask) && embedded.contains(skill)
                ? similarities.getOrDefault(ask + "|" + skill, 0.0) : 0;
    }

    private static int score(Map<Job, MatchResult> results, Job job) {
        return results.get(job).totalScore();
    }

    private static WorkExperience role(String title, String description) {
        return new WorkExperience(UUID.randomUUID(), UUID.randomUUID(), "Firma", title, null,
                description, LocalDate.of(2020, 1, 1), null, true, List.of(), List.of(), 0, null,
                null, List.of());
    }

    private static JobRequirement required(String text) {
        return new JobRequirement(text, RequirementTier.REQUIRED, RequirementKind.EXPERIENCE,
                null);
    }

    private static Job posting(JobRequirement... asks) {
        UUID id = UUID.randomUUID();
        return Job.builder().id(id).url("https://example.com/" + id).title("Stilling")
                .companyName("Firma").isActive(true).technologies(HELD_ASKS)
                .requiredSkills(HELD_ASKS).requirements(List.of(asks)).build();
    }
}
