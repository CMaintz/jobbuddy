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
import com.autoapplicant.domain.skill.ParsedSkillSuggestion;
import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.domain.skill.SkillNames;
import com.autoapplicant.domain.user.Education;
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
import com.autoapplicant.port.out.user.CertificationRepositoryPort;
import com.autoapplicant.port.out.user.EducationRepositoryPort;
import com.autoapplicant.port.out.user.PreferencesRepositoryPort;
import com.autoapplicant.port.out.user.ProfileEmbeddingRepositoryPort;
import com.autoapplicant.port.out.user.ProfileRepositoryPort;
import com.autoapplicant.port.out.user.WorkExperienceRepositoryPort;
import com.autoapplicant.usecase.skills.SkillCanonicalizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.assertj.core.data.Offset;
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
 * measured on the wording real postings use. Each profile also carries an invented dated work
 * history (#346), written before scoring, so specialist experience asks are measured the same way.
 *
 * <p>Asks answered by meaning (#350) are scored from frozen label similarities
 * ({@code skill-similarity.json}) and each profile's frozen suggested skills, both described in
 * their fixtures. The thresholds were again chosen on the tuning rows and the holdout rows
 * scored once afterwards. Every table prints the score on main ({@code main-scores.json})
 * beside it.
 */
@Import({SkillCanonicalizer.class, SkillTaxonomyPersistenceAdapter.class})
class MatchScoreEvalTest extends PostgresIntegrationTest {

    private static final String COMMS = "comms-grc";
    /** The comms profile as a CV import stores it: taxonomy skills under English master names. */
    private static final String COMMS_ENGLISH = "comms-grc-en";
    private static final String ENGINEER = "engineer";
    private static final String GOOD = "G";
    private static final String MISMATCH = "M";
    private static final Set<String> COMMS_FIELD = Set.of("comms", "grc", "kommune");
    private static final Set<String> ENGINEER_FIELD = Set.of("eng");
    private static final int FEED_SIZE = 100;
    private static final String OUT_OF_REACH = "Kræver ";
    private static final int HIGHEST_MISMATCH_SCORE = 76;
    /** How far apart two spellings of the same profile may score on one posting. */
    private static final int SAME_PROFILE_TOLERANCE = 3;

    /**
     * Posts inside the comms profile's own field that demand a law degree. Their skill labels
     * make them look like good fits; only their requirements rule them out.
     */
    private static final Set<String> LAW_DEGREE_POSTS = Set.of(
            "jurist-med-fokus-paa-informationssikkerh", "compliance-og-gdpr-specialist-hos-forsyn");

    /**
     * Posts inside the comms profile's own field that require hands-on security experience
     * (security architecture, incident response) with no year count. Asking for experience is
     * normal, so only a senior ask may rule a post out; these score on their labels.
     */
    private static final Set<String> JUNIOR_SECURITY_ASK_POSTS = Set.of(
            "vil-du-vaere-med-til-at-saette-retningen", "cybersikkerhedsspecialist-til-kritisk-in");

    /** The day the eval scores on, so the profiles' current roles don't grow with the calendar. */
    private static final Clock SCORED_ON = Clock.fixed(
            LocalDate.of(2026, 6, 1).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

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

    @Test
    void a_security_post_asking_experience_without_years_is_not_ruled_out_for_the_comms_profile()
            throws IOException {
        Map<String, MatchResult> results = resultsFor(COMMS);

        JUNIOR_SECURITY_ASK_POSTS.forEach(id -> assertThat(results.get(id).matchReasons()).as(id)
                .noneMatch(reason -> reason.startsWith(OUT_OF_REACH)));
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
    void no_good_or_plausible_fit_scores_below_main(String profile) throws IOException {
        Map<String, Integer> scores = scores(profile);
        JsonNode main = mainScores(profile);

        assertThat(idsWhere(p -> !fit(p, profile).equals(MISMATCH))).allSatisfy(id ->
                assertThat(scores.get(id)).as(id).isGreaterThanOrEqualTo(main.get(id).asInt()));
    }

    /**
     * A CV import stores "Public Relations" where she wrote "Pressearbejde". Her matches must not
     * depend on which spelling she has. The unenriched rows are left out: their text is searched
     * for the label as she wrote it, never for its taxonomy aliases.
     */
    @Test
    void english_taxonomy_names_score_like_the_danish_skills_they_stand_for() throws IOException {
        Map<String, MatchResult> danish = resultsFor(COMMS);
        Map<String, MatchResult> english = resultsFor(COMMS_ENGLISH);

        assertThat(idsWhere(p -> !p.get("set").asText().equals("unenriched"))).allSatisfy(id ->
                assertThat(english.get(id).totalScore()).as(id)
                        .isCloseTo(danish.get(id).totalScore(),
                                Offset.offset(SAME_PROFILE_TOLERANCE)));
    }

    /** Main's highest clear mismatch across both profiles is the ceiling for every one now. */
    @Test
    void no_clear_mismatch_rises_above_mains_highest() throws IOException {
        int mainHighest = 0;
        int highest = 0;
        for (String profile : List.of(COMMS, ENGINEER)) {
            JsonNode main = mainScores(profile);
            Map<String, Integer> scores = scores(profile);
            for (String id : idsWhere(clearMismatches(profile))) {
                mainHighest = Math.max(mainHighest, main.get(id).asInt());
                highest = Math.max(highest, scores.get(id));
            }
        }

        assertThat(highest).isLessThanOrEqualTo(mainHighest);
    }

    @Test
    void a_communications_fit_reaches_the_top_band() throws IOException {
        Map<String, Integer> scores = scores(COMMS);

        assertThat(MatchLabel.fromScore(highest(scores,
                p -> fit(p, COMMS).equals(GOOD) && p.get("kind").asText().equals("comms"))))
                .isEqualTo(MatchLabel.EXCELLENT);
    }

    @ParameterizedTest
    @ValueSource(strings = {COMMS, ENGINEER})
    void no_mismatch_reaches_the_top_band(String profile) throws IOException {
        Map<String, Integer> scores = scores(profile);

        // Held below 80 with room to spare: a preference bonus in a real feed adds up to 8 more.
        // 76 is the technical security post's score before the top band was lifted.
        assertThat(highest(scores, p -> fit(p, profile).equals(MISMATCH)))
                .isLessThanOrEqualTo(HIGHEST_MISMATCH_SCORE);
    }

    /**
     * A posting outside the profile's own field and labelled a mismatch. Mismatches inside the
     * field are left out: they share a GRC profile's skill labels and differ in the experience
     * they ask for, which the scorer weighs only for senior asks in catalogued specialist areas
     * (a GDPR post asking three years of legal advice, or a security post asking architecture
     * experience with no year count, still scores on its labels).
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

    /** Each posting's score on main, frozen in {@code main-scores.json}. */
    private JsonNode mainScores(String profile) throws IOException {
        return fixture("main-scores.json").get("scores").get(profile);
    }

    private List<String> idsWhere(Predicate<JsonNode> which) {
        List<String> out = new ArrayList<>();
        postings.forEach(p -> {
            if (which.test(p)) out.add(p.get("id").asText());
        });
        return out;
    }

    /** The scores, printed beside each posting's score on main. */
    private Map<String, Integer> scores(String profileKey) throws IOException {
        JsonNode main = mainScores(profileKey);
        Map<String, Integer> scores = new LinkedHashMap<>();
        resultsFor(profileKey).forEach((id, result) -> scores.put(id, result.totalScore()));
        StringBuilder table = new StringBuilder(System.lineSeparator()).append(profileKey)
                .append(": set | fit | main | score | posting").append(System.lineSeparator());
        postings.forEach(p -> table.append(p.get("set").asText()).append(" | ")
                .append(fit(p, profileKey)).append(" | ")
                .append(main.get(p.get("id").asText()).asInt()).append(" | ")
                .append(scores.get(p.get("id").asText())).append(" | ")
                .append(p.get("id").asText()).append(System.lineSeparator()));
        System.out.println(table);
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

    private MatchingService serviceFor(UUID userId, JsonNode profile, List<Job> jobs)
            throws IOException {
        ProfileRepositoryPort profiles = mock(ProfileRepositoryPort.class);
        when(profiles.findByUserId(userId)).thenReturn(Optional.of(new Profile(UUID.randomUUID(),
                userId, profile.get("headline").asText(), profile.get("summary").asText(),
                null, List.of(), List.of(), null, null, null, null, null, null, null)));
        ProfileSkillRepositoryPort skills = mock(ProfileSkillRepositoryPort.class);
        when(skills.findByUserId(userId)).thenReturn(skillRows(userId, profile.get("skills")));
        ParsedSkillSuggestionRepositoryPort suggested =
                mock(ParsedSkillSuggestionRepositoryPort.class);
        when(suggested.findByUserId(userId))
                .thenReturn(suggestionRows(userId, profile.get("suggested")));
        JsonNode pairs = fixture("skill-similarity.json").get("pairs");
        EducationRepositoryPort education = mock(EducationRepositoryPort.class);
        when(education.findByUserId(userId))
                .thenReturn(educationRows(userId, profile.get("education")));
        WorkExperienceRepositoryPort experience = mock(WorkExperienceRepositoryPort.class);
        when(experience.findByUserId(userId))
                .thenReturn(experienceRows(userId, profile.get("experience")));
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
                new MatchProfileLoader(canonicalizer, new MatchProfileLoader.Sources(skills,
                        suggested, education, mock(CertificationRepositoryPort.class), experience),
                        labels -> frozenSimilarity(pairs, labels), SCORED_ON));
    }

    /**
     * Similarities as text-embedding-3-small measured them, frozen so the eval stays offline. A
     * label the scorer did not hand over for embedding has no vector in production, so it gets
     * none here either.
     */
    private static SkillSimilarity frozenSimilarity(JsonNode pairs, Collection<String> labels) {
        Set<String> embedded = labels.stream().map(SkillNames::normalize)
                .collect(Collectors.toSet());
        return (ask, skill) -> {
            String a = SkillNames.normalize(ask);
            String b = SkillNames.normalize(skill);
            return embedded.contains(a) && embedded.contains(b) ? pairs.path(a).path(b).asDouble(0)
                    : 0;
        };
    }

    private static List<ParsedSkillSuggestion> suggestionRows(UUID userId, JsonNode rows) {
        List<ParsedSkillSuggestion> out = new ArrayList<>();
        rows.forEach(r -> out.add(new ParsedSkillSuggestion(UUID.randomUUID(), userId,
                r.get("skill").asText(), SkillNames.normalize(r.get("skill").asText()),
                r.get("because").asText(), ParsedSkillSuggestion.Source.ADJACENT)));
        return out;
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

    private static List<WorkExperience> experienceRows(UUID userId, JsonNode rows) {
        List<WorkExperience> out = new ArrayList<>();
        rows.forEach(e -> out.add(new WorkExperience(UUID.randomUUID(), userId,
                e.get("company").asText(), e.get("title").asText(), null,
                e.get("description").asText(), LocalDate.parse(e.get("start").asText()),
                e.get("end").isNull() ? null : LocalDate.parse(e.get("end").asText()),
                e.get("end").isNull(), List.of(), List.of(), 0, null, null, List.of())));
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
