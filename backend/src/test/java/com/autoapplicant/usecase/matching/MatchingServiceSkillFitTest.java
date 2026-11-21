package com.autoapplicant.usecase.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.job.Job;
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
import com.autoapplicant.port.out.skills.ParsedSkillSuggestionRepositoryPort;
import com.autoapplicant.port.out.skills.ProfileSkillRepositoryPort;
import com.autoapplicant.port.out.skills.SkillTaxonomyRepositoryPort;
import com.autoapplicant.port.out.user.CertificationRepositoryPort;
import com.autoapplicant.port.out.user.EducationRepositoryPort;
import com.autoapplicant.port.out.user.PreferencesRepositoryPort;
import com.autoapplicant.port.out.user.ProfileEmbeddingRepositoryPort;
import com.autoapplicant.port.out.user.ProfileRepositoryPort;
import com.autoapplicant.usecase.skills.SkillCanonicalizer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * How the skill-fit part of a match score reads real postings: long lists of asks, Danish
 * compound skills, skills a profile only names in its own summary, and postings that were never
 * enriched. Each case here once scored a good fit as weak.
 */
@ExtendWith(MockitoExtension.class)
class MatchingServiceSkillFitTest {

    private static final String NO_SUMMARY = "";

    @Mock JobEmbeddingRepositoryPort embeddingRepo;
    @Mock JobRepositoryPort jobRepo;
    @Mock ProfileRepositoryPort profileRepo;
    @Mock PreferencesRepositoryPort prefsRepo;
    @Mock EmbeddingProviderPort aiProvider;
    @Mock IgnoredJobRepositoryPort ignoredJobRepo;
    @Mock RecommendationFeedbackRepositoryPort feedbackRepo;
    @Mock ProfileEmbeddingRepositoryPort profileEmbeddingRepo;
    @Mock ProfileSkillRepositoryPort profileSkillRepo;
    @Mock ApplicationRepositoryPort applicationRepo;
    @Mock SkillTaxonomyRepositoryPort taxonomyRepo;
    @Mock EducationRepositoryPort educationRepo;
    @Mock CertificationRepositoryPort certificationRepo;
    @Mock ParsedSkillSuggestionRepositoryPort suggestionRepo;

    MatchingService service;
    final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new MatchingService(embeddingRepo, jobRepo, profileRepo, prefsRepo,
                aiProvider, ignoredJobRepo, feedbackRepo, profileEmbeddingRepo, applicationRepo,
                new MatchProfileLoader(new SkillCanonicalizer(taxonomyRepo),
                        new MatchProfileLoader.Sources(profileSkillRepo, suggestionRepo,
                                educationRepo, certificationRepo),
                        labels -> SkillSimilarity.NONE));
    }

    @Test
    void holding_the_core_of_a_long_requirement_list_is_a_strong_match() {
        // A real backend posting: enrichment lists nine requirements, and a developer who holds
        // the stack still cannot hold "Clean code" or "Big data processing" as a profile skill.
        givenProfile(NO_SUMMARY, "Java", "Spring Boot", "PostgreSQL", "Microservices", "Kafka");
        Job job = required("Java", "Spring Boot", "Microservices", "Big data processing",
                "PostgreSQL", "Elasticsearch", "Apache Kafka", "Software architecture", "Clean code");

        assertThat(resultFor(job).matchLabel()).isIn(MatchLabel.STRONG, MatchLabel.EXCELLENT);
    }

    @Test
    void a_danish_compound_skill_counts_toward_the_field_it_belongs_to() {
        givenProfile(NO_SUMMARY, "Intern kommunikation", "Projektkoordinering", "Kampagneplanlægning");
        Job comms = required("Kommunikation", "Koordinering", "Planlægning");
        Job bookkeeping = required("Bogholderi", "Afstemninger", "Fakturering");

        List<MatchResult> results = feed(comms, bookkeeping);

        assertThat(scoreOf(results, comms)).isGreaterThan(scoreOf(results, bookkeeping));
    }

    @Test
    void a_skill_named_in_the_profile_summary_counts_toward_an_ask() {
        givenProfile("Kommunikation og informationssikkerhed, med kursus i NIS2.", "Pressearbejde");
        Job security = required("Informationssikkerhed");
        Job bookkeeping = required("Bogholderi");

        List<MatchResult> results = feed(security, bookkeeping);

        assertThat(scoreOf(results, security)).isGreaterThan(scoreOf(results, bookkeeping));
    }

    @Test
    void an_unenriched_posting_is_scored_on_the_profile_skills_its_text_names() {
        givenProfile(NO_SUMMARY, "Java", "Spring Boot", "PostgreSQL");
        Job named = unenriched("Vi bygger vores platform i Java og Spring Boot på PostgreSQL.");
        Job silent = unenriched("Vi søger en erfaren medarbejder til vores lager.");

        List<MatchResult> results = feed(named, silent);

        assertThat(scoreOf(results, named)).isGreaterThan(scoreOf(results, silent));
    }

    @Test
    void a_short_head_does_not_relate_two_skills() {
        // "SQL" sits inside "PostgreSQL", but holding PostgreSQL is not evidence of SQL Server
        // work, and a three-letter head would relate far too much.
        givenProfile(NO_SUMMARY, "PostgreSQL");
        Job sql = required("SQL");
        Job bookkeeping = required("Bogholderi");

        List<MatchResult> results = feed(sql, bookkeeping);

        assertThat(scoreOf(results, sql)).isEqualTo(scoreOf(results, bookkeeping));
    }

    @Test
    void only_the_last_word_of_a_label_relates_it() {
        // Comparing the first word would relate "Intern audit" to "Intern kommunikation".
        givenProfile(NO_SUMMARY, "Intern kommunikation");
        Job audit = required("Intern audit");
        Job bookkeeping = required("Bogholderi");

        List<MatchResult> results = feed(audit, bookkeeping);

        assertThat(scoreOf(results, audit)).isEqualTo(scoreOf(results, bookkeeping));
    }

    @Test
    void sharing_a_generic_head_word_does_not_make_a_mismatch_a_good_fit() {
        // Every one of these ends on "management", and none of them is project management.
        givenProfile(NO_SUMMARY, "Project management");
        Job procurement = required("Risk management", "Change management", "Vendor management",
                "Stakeholder management", "Contract management");

        assertThat(resultFor(procurement).matchLabel()).isEqualTo(MatchLabel.WEAK);
    }

    @Test
    void related_skills_alone_cannot_reach_the_top_of_the_scale() {
        // Many loosely related asks are still not one skill the profile actually holds.
        givenProfile(NO_SUMMARY, "Intern kommunikation");
        Job loose = required("Kommunikation", "Digital kommunikation", "Visuel kommunikation",
                "Krisekommunikation", "Ledelseskommunikation", "Kommunikationsstrategi");

        assertThat(resultFor(loose).matchLabel()).isIn(MatchLabel.WEAK, MatchLabel.MODERATE);
    }

    @Test
    void a_hyphenated_compound_keeps_its_whole_head() {
        // Split at the hyphen, "IT-sikkerhed" would end on "sikkerhed" and half-answer a posting
        // that demands a security clearance.
        givenProfile(NO_SUMMARY, "IT-sikkerhed");
        Job clearance = required("Sikkerhedsgodkendelse");
        Job bookkeeping = required("Bogholderi");

        List<MatchResult> results = feed(clearance, bookkeeping);

        assertThat(scoreOf(results, clearance)).isEqualTo(scoreOf(results, bookkeeping));
    }

    @Test
    void a_related_skill_is_not_reported_as_a_covered_requirement() {
        givenProfile(NO_SUMMARY, "Intern kommunikation");
        Job comms = required("Kommunikation");

        List<String> reasons = resultFor(comms).matchReasons();

        assertThat(reasons).contains("0/1 required skill covered", "Missing required: Kommunikation");
    }

    @Test
    void a_share_button_in_an_unenriched_posting_is_not_a_skill_it_asks_for() {
        givenProfile(NO_SUMMARY, "Sociale medier");
        Job shared = unenriched("Vi søger en lagermedarbejder.\nDel på sociale medier");
        Job plain = unenriched("Vi søger en lagermedarbejder.");

        List<MatchResult> results = feed(shared, plain);

        assertThat(scoreOf(results, shared)).isEqualTo(scoreOf(results, plain));
    }

    @Test
    void holding_most_of_what_a_posting_demands_reaches_the_top_band() {
        givenProfile(NO_SUMMARY, "Intern kommunikation", "Projektkoordinering", "SharePoint",
                "InDesign");
        Job job = required("Intern kommunikation", "Projektkoordinering", "SharePoint",
                "InDesign", "Photoshop");

        assertThat(resultFor(job).matchLabel()).isEqualTo(MatchLabel.EXCELLENT);
    }

    @Test
    void related_skills_count_half_toward_answering_a_postings_demands() {
        // Three held and three only related: the shape of a technical security post read by a
        // GRC profile. Counting the related ones in full would put it in the top band.
        givenProfile(NO_SUMMARY, "SharePoint", "InDesign", "Photoshop", "Projektkoordinering",
                "Intern kommunikation", "Kampagneplanlægning");
        Job job = required("SharePoint", "InDesign", "Photoshop", "Koordinering", "Kommunikation",
                "Planlægning", "Bogholderi", "Fakturering");

        assertThat(resultFor(job).matchLabel()).isEqualTo(MatchLabel.STRONG);
    }

    @Test
    void a_degree_the_profile_lacks_puts_a_matching_posting_out_of_reach_and_says_why() {
        // The skills all match; the posting is still for lawyers only.
        givenProfile(NO_SUMMARY, "GDPR", "NIS2", "Informationssikkerhed");
        givenEducation("Kandidat", "Kommunikation");
        Job lawyer = posting().technologies(List.of("GDPR", "NIS2", "Informationssikkerhed"))
                .requiredSkills(List.of("GDPR", "NIS2", "Informationssikkerhed"))
                .requirements(List.of(new JobRequirement("Du er uddannet cand.jur.",
                        RequirementTier.REQUIRED, RequirementKind.EDUCATION, null)))
                .build();

        MatchResult result = resultFor(lawyer);

        assertThat(result.matchLabel()).isEqualTo(MatchLabel.WEAK);
        assertThat(result.matchReasons()).first().isEqualTo("Kræver cand.jur.");
    }

    private void givenEducation(String degree, String field) {
        when(educationRepo.findByUserId(userId)).thenReturn(List.of(new Education(
                UUID.randomUUID(), userId, null, degree, field, null, null, null, null, 0,
                null, null, List.of())));
    }

    private void givenProfile(String summary, String... skills) {
        when(profileRepo.findByUserId(userId)).thenReturn(Optional.of(new Profile(UUID.randomUUID(),
                userId, "Headline", summary, null, List.of(), List.of(),
                null, null, null, null, null, null, null)));
        when(profileSkillRepo.findByUserId(userId)).thenReturn(List.of(skills).stream()
                .map(name -> new ProfileSkill(UUID.randomUUID(), userId, name,
                        null, null, null, false, 0, null))
                .toList());
    }

    private List<MatchResult> feed(Job... jobs) {
        when(aiProvider.embed(anyString())).thenReturn(new float[] {1f});
        when(embeddingRepo.findNearestNeighborJobIds(any(), anyInt()))
                .thenReturn(List.of(jobs).stream().map(Job::id).toList());
        when(jobRepo.findByIds(any())).thenReturn(List.of(jobs));
        return service.getRecommendations(userId, 10);
    }

    private MatchResult resultFor(Job job) {
        return feed(job).get(0);
    }

    private static Job required(String... asks) {
        return posting().technologies(List.of(asks)).requiredSkills(List.of(asks)).build();
    }

    private static Job unenriched(String description) {
        return posting().descriptionClean(description).build();
    }

    private static Job.Builder posting() {
        UUID id = UUID.randomUUID();
        return Job.builder().id(id).url("https://example.com/" + id).title("Stilling")
                .companyName("Firma").isActive(true);
    }

    private static int scoreOf(List<MatchResult> results, Job job) {
        return results.stream().filter(r -> r.jobId().equals(job.id())).findFirst()
                .orElseThrow().totalScore();
    }
}
