package com.autoapplicant.usecase.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.autoapplicant.adapter.persistence.PostgresIntegrationTest;
import com.autoapplicant.adapter.persistence.adapter.SkillTaxonomyPersistenceAdapter;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.JobCategory;
import com.autoapplicant.domain.job.JobRequirement;
import com.autoapplicant.domain.job.RequirementKind;
import com.autoapplicant.domain.job.RequirementTier;
import com.autoapplicant.domain.matching.MatchLabel;
import com.autoapplicant.domain.matching.MatchResult;
import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.domain.user.Education;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.port.out.ai.EmbeddingProviderPort;
import com.autoapplicant.port.out.application.ApplicationRepositoryPort;
import com.autoapplicant.port.out.job.IgnoredJobRepositoryPort;
import com.autoapplicant.port.out.job.JobEmbeddingRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.matching.RecommendationFeedbackRepositoryPort;
import com.autoapplicant.port.out.skills.ProfileSkillRepositoryPort;
import com.autoapplicant.port.out.user.CertificationRepositoryPort;
import com.autoapplicant.port.out.user.EducationRepositoryPort;
import com.autoapplicant.port.out.user.PreferencesRepositoryPort;
import com.autoapplicant.port.out.user.ProfileEmbeddingRepositoryPort;
import com.autoapplicant.port.out.user.ProfileRepositoryPort;
import com.autoapplicant.usecase.skills.SkillCanonicalizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * Scores real Danish postings against a communications/GRC profile and an engineer profile, and
 * holds the scorer to what a person reading those postings would say about the fit.
 *
 * <p>The postings were enriched by the production prompt and parser and frozen in
 * {@code matching-eval/postings.json}; the profiles are anonymised CVs run through the CV parse
 * prompt. Each posting carries a fit label per profile, written before anything was scored. The
 * taxonomy is the migrated one, because alias folding is half of what decides a score.
 *
 * <p>Run with {@code --info} to see the score table. The scorer's constants were chosen on the
 * rows marked {@code tuning}; the {@code holdout} rows were labelled and scored only afterwards,
 * so a change that passes on tuning rows alone is overfitting. The {@code unenriched} rows repeat
 * three postings with no ask lists and their crawled page text, chrome included, so the fallback
 * that reads the text is held to the same bar. Each row still online when #338 came along also
 * carries its frozen requirements (degrees, certifications), so the hard-requirement gate is
 * measured on the wording real postings use.
 */
@Import({SkillCanonicalizer.class, SkillTaxonomyPersistenceAdapter.class})
class MatchScoreEvalTest extends PostgresIntegrationTest {

    private static final String COMMS = "comms-grc";
    private static final String ENGINEER = "engineer";
    private static final String GOOD = "G";
    private static final String MISMATCH = "M";
    private static final Set<String> COMMS_FIELD = Set.of("comms", "grc", "kommune");
    private static final Set<String> ENGINEER_FIELD = Set.of("eng");
    private static final int FEED_SIZE = 100;
    private static final String OUT_OF_REACH = "Kræver ";

    /**
     * Posts inside the comms profile's own field that demand a law degree. Their skill labels
     * make them look like good fits; only their requirements rule them out.
     */
    private static final Set<String> LAW_DEGREE_POSTS = Set.of(
            "jurist-med-fokus-paa-informationssikkerh", "compliance-og-gdpr-specialist-hos-forsyn");

    @Autowired SkillCanonicalizer canonicalizer;

    private final ObjectMapper json = new ObjectMapper();
    private JsonNode postings;

    @BeforeEach
    void loadPostings() throws IOException {
        postings = fixture("postings.json").get("postings");
    }

    @ParameterizedTest
    @ValueSource(strings = {COMMS, ENGINEER})
    void every_good_fit_outscores_every_clear_mismatch(String profile) throws IOException {
        Map<String, Integer> scores = scores(profile);

        assertThat(lowest(scores, profile, GOOD))
                .isGreaterThan(highest(scores, clearMismatches(profile)));
    }

    @ParameterizedTest
    @ValueSource(strings = {COMMS, ENGINEER})
    void clear_mismatches_score_weak(String profile) throws IOException {
        Map<String, Integer> scores = scores(profile);

        assertThat(MatchLabel.fromScore(highest(scores, clearMismatches(profile))))
                .isEqualTo(MatchLabel.WEAK);
    }

    @ParameterizedTest
    @ValueSource(strings = {COMMS, ENGINEER})
    void at_least_half_the_good_fits_score_strong(String profile) throws IOException {
        Map<String, Integer> scores = scores(profile);
        List<Integer> good = scoresWhere(scores, p -> fit(p, profile).equals(GOOD));
        long strong = good.stream().map(MatchLabel::fromScore)
                .filter(label -> label == MatchLabel.STRONG || label == MatchLabel.EXCELLENT)
                .count();

        assertThat(strong * 2).isGreaterThanOrEqualTo(good.size());
    }

    @ParameterizedTest
    @ValueSource(strings = {COMMS, ENGINEER})
    void no_good_or_plausible_fit_is_ruled_out_by_a_hard_requirement(String profile)
            throws IOException {
        Map<String, MatchResult> results = resultsFor(profile);

        postings.forEach(p -> {
            String id = p.get("id").asText();
            if (!fit(p, profile).equals(MISMATCH)) {
                assertThat(results.get(id).matchReasons()).as(id)
                        .noneMatch(reason -> reason.startsWith(OUT_OF_REACH));
            }
        });
    }

    @Test
    void a_law_degree_post_in_the_comms_field_scores_weak_and_says_why() throws IOException {
        Map<String, MatchResult> results = resultsFor(COMMS);

        LAW_DEGREE_POSTS.forEach(id -> {
            assertThat(results.get(id).matchLabel()).as(id).isEqualTo(MatchLabel.WEAK);
            assertThat(results.get(id).matchReasons()).as(id).first().isEqualTo("Kræver cand.jur.");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {COMMS, ENGINEER})
    void the_best_good_fit_reaches_the_top_band(String profile) throws IOException {
        Map<String, Integer> scores = scores(profile);

        assertThat(MatchLabel.fromScore(highest(scores, p -> fit(p, profile).equals(GOOD))))
                .isEqualTo(MatchLabel.EXCELLENT);
    }

    @ParameterizedTest
    @ValueSource(strings = {COMMS, ENGINEER})
    void no_mismatch_reaches_the_top_band(String profile) throws IOException {
        Map<String, Integer> scores = scores(profile);

        assertThat(MatchLabel.fromScore(highest(scores, p -> fit(p, profile).equals(MISMATCH))))
                .isNotEqualTo(MatchLabel.EXCELLENT);
    }

    /**
     * A posting outside the profile's own field and labelled a mismatch. Mismatches inside the
     * field are left out: a technical security post or a SOC post shares a GRC profile's skill
     * labels and differs in the experience it asks for, which the scorer does not weigh.
     */
    private Predicate<JsonNode> clearMismatches(String profile) {
        Set<String> ownField = profile.equals(COMMS) ? COMMS_FIELD : ENGINEER_FIELD;
        return p -> fit(p, profile).equals(MISMATCH) && !ownField.contains(p.get("kind").asText());
    }

    private int lowest(Map<String, Integer> scores, String profile, String label) {
        return scoresWhere(scores, p -> fit(p, profile).equals(label)).stream()
                .min(Integer::compare).orElseThrow();
    }

    private int highest(Map<String, Integer> scores, Predicate<JsonNode> which) {
        return scoresWhere(scores, which).stream().max(Integer::compare).orElseThrow();
    }

    private List<Integer> scoresWhere(Map<String, Integer> scores, Predicate<JsonNode> which) {
        List<Integer> out = new ArrayList<>();
        postings.forEach(p -> {
            if (which.test(p)) out.add(scores.get(p.get("id").asText()));
        });
        return out;
    }

    private static String fit(JsonNode posting, String profile) {
        return posting.get("fit").get(profile).asText();
    }

    private Map<String, Integer> scores(String profileKey) throws IOException {
        Map<String, Integer> scores = scoresFor(profileKey);
        StringBuilder table = new StringBuilder(System.lineSeparator())
                .append(profileKey).append(": fit | score | posting").append(System.lineSeparator());
        postings.forEach(p -> table.append(fit(p, profileKey)).append(" | ")
                .append(scores.get(p.get("id").asText())).append(" | ")
                .append(p.get("id").asText()).append(System.lineSeparator()));
        System.out.println(table);
        return scores;
    }

    private Map<String, Integer> scoresFor(String profileKey) throws IOException {
        Map<String, Integer> scores = new LinkedHashMap<>();
        resultsFor(profileKey).forEach((id, result) -> scores.put(id, result.totalScore()));
        return scores;
    }

    private Map<String, MatchResult> resultsFor(String profileKey) throws IOException {
        JsonNode profile = fixture("profiles.json").get("profiles").get(profileKey);
        UUID userId = UUID.randomUUID();
        Map<UUID, String> idsByJob = new LinkedHashMap<>();
        List<Job> jobs = new ArrayList<>();
        for (JsonNode p : postings) {
            Job job = toJob(p);
            idsByJob.put(job.id(), p.get("id").asText());
            jobs.add(job);
        }
        MatchingService service = serviceFor(userId, profile, jobs);
        Map<String, MatchResult> byPosting = new LinkedHashMap<>();
        for (MatchResult r : service.getRecommendations(userId, FEED_SIZE)) {
            byPosting.put(idsByJob.get(r.jobId()), r);
        }
        return byPosting;
    }

    private MatchingService serviceFor(UUID userId, JsonNode profile, List<Job> jobs) {
        ProfileRepositoryPort profiles = mock(ProfileRepositoryPort.class);
        when(profiles.findByUserId(userId)).thenReturn(Optional.of(new Profile(UUID.randomUUID(),
                userId, profile.get("headline").asText(), profile.get("summary").asText(),
                null, List.of(), List.of(), null, null, null, null, null, null, null)));
        ProfileSkillRepositoryPort skills = mock(ProfileSkillRepositoryPort.class);
        when(skills.findByUserId(userId)).thenReturn(skillRows(userId, profile.get("skills")));
        EducationRepositoryPort education = mock(EducationRepositoryPort.class);
        when(education.findByUserId(userId))
                .thenReturn(educationRows(userId, profile.get("education")));
        EmbeddingProviderPort ai = mock(EmbeddingProviderPort.class);
        when(ai.embed(anyString())).thenReturn(new float[] {1f});
        JobEmbeddingRepositoryPort embeddings = mock(JobEmbeddingRepositoryPort.class);
        when(embeddings.findNearestNeighborJobIds(any(), anyInt()))
                .thenReturn(jobs.stream().map(Job::id).toList());
        JobRepositoryPort jobRepo = mock(JobRepositoryPort.class);
        when(jobRepo.findByIds(any())).thenReturn(jobs);
        return new MatchingService(embeddings, jobRepo, profiles,
                mock(PreferencesRepositoryPort.class), ai, mock(IgnoredJobRepositoryPort.class),
                mock(RecommendationFeedbackRepositoryPort.class),
                mock(ProfileEmbeddingRepositoryPort.class), mock(ApplicationRepositoryPort.class),
                new MatchProfileLoader(canonicalizer, skills, education,
                        mock(CertificationRepositoryPort.class)));
    }

    private static List<ProfileSkill> skillRows(UUID userId, JsonNode names) {
        List<ProfileSkill> rows = new ArrayList<>();
        names.forEach(n -> rows.add(new ProfileSkill(UUID.randomUUID(), userId, n.asText(),
                null, null, null, false, 0, null)));
        return rows;
    }

    private static List<Education> educationRows(UUID userId, JsonNode rows) {
        List<Education> out = new ArrayList<>();
        rows.forEach(e -> out.add(new Education(UUID.randomUUID(), userId, null,
                e.get("degree").asText(), e.get("field").asText(), null, null, null, null, 0,
                null, null, List.of())));
        return out;
    }

    private static Job toJob(JsonNode p) {
        return Job.builder().id(UUID.randomUUID()).url("https://example.com/" + p.get("id").asText())
                .title(p.get("title").asText()).isActive(true)
                .descriptionClean(p.path("description").asText(null))
                .jobCategory(JobCategory.valueOf(p.get("jobCategory").asText()))
                .technologies(strings(p.get("technologies"))).skills(strings(p.get("skills")))
                .requiredSkills(strings(p.get("requiredSkills")))
                .preferredSkills(strings(p.get("preferredSkills")))
                .requirements(requirements(p.path("requirements")))
                .build();
    }

    private static List<JobRequirement> requirements(JsonNode array) {
        List<JobRequirement> out = new ArrayList<>();
        array.forEach(r -> out.add(new JobRequirement(r.get("text").asText(),
                RequirementTier.parse(r.get("tier").asText()),
                RequirementKind.parse(r.get("kind").asText()), null)));
        return out;
    }

    private static List<String> strings(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
    }

    private JsonNode fixture(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/matching-eval/" + name)) {
            return json.readTree(in);
        }
    }
}
