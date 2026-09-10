package com.autoapplicant.usecase.skills;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.domain.skill.SkillCandidate;
import com.autoapplicant.domain.skill.SkillConfirmation;
import com.autoapplicant.domain.skill.SkillTaxonomy;
import com.autoapplicant.port.out.skills.ProfileSkillRepositoryPort;
import com.autoapplicant.domain.skill.ParsedSkillSuggestion;
import com.autoapplicant.port.out.skills.ParsedSkillSuggestionRepositoryPort;
import com.autoapplicant.port.out.skills.SkillCandidateDismissalRepositoryPort;
import com.autoapplicant.port.out.skills.SkillTaxonomyRepositoryPort;
import com.autoapplicant.usecase.job.MarketCorpusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

class SkillCandidateServiceTest {

    private static final UUID USER = UUID.randomUUID();

    private final ProfileSkillRepositoryPort profileSkillRepo = Mockito.mock(ProfileSkillRepositoryPort.class);
    private final SkillTaxonomyRepositoryPort taxonomyRepo = Mockito.mock(SkillTaxonomyRepositoryPort.class);
    private final MarketCorpusService marketCorpus = Mockito.mock(MarketCorpusService.class);

    /** Real dismissal store: "never suggest this again" is behaviour worth testing end to end. */
    private final Set<String> dismissed = new HashSet<>();
    private final SkillCandidateDismissalRepositoryPort dismissalRepo =
            new SkillCandidateDismissalRepositoryPort() {
                @Override public Set<String> findDismissedNames(UUID userId) { return dismissed; }
                @Override public void dismiss(UUID userId, String normalizedName) { dismissed.add(normalizedName); }
            };

    /** Real suggestion queue too: what a confirmed or declined suggestion does to it is behaviour. */
    private final List<ParsedSkillSuggestion> parsedSuggestions = new ArrayList<>();
    private final ParsedSkillSuggestionRepositoryPort parsedSuggestionRepo =
            new ParsedSkillSuggestionRepositoryPort() {
                @Override public List<ParsedSkillSuggestion> findByUserId(UUID userId) {
                    return List.copyOf(parsedSuggestions);
                }
                @Override public void record(ParsedSkillSuggestion s) {
                    if (parsedSuggestions.stream().noneMatch(e -> e.normalizedName().equals(s.normalizedName()))) {
                        parsedSuggestions.add(s);
                    }
                }
                @Override public void remove(UUID userId, String normalizedName) {
                    parsedSuggestions.removeIf(s -> s.normalizedName().equals(normalizedName));
                }
            };

    private final SkillCandidateService service = new SkillCandidateService(
            profileSkillRepo, taxonomyRepo, dismissalRepo, marketCorpus, parsedSuggestionRepo,
            new SkillResolver(taxonomyRepo, new SkillCanonicalizer(taxonomyRepo)));

    @BeforeEach
    void setUp() {
        when(profileSkillRepo.findByUserId(USER)).thenReturn(List.of());
        when(taxonomyRepo.findByNormalizedName(any())).thenReturn(Optional.empty());
        when(taxonomyRepo.findByParentIds(any())).thenReturn(List.of());
        when(marketCorpus.collect(any(), anyBoolean())).thenReturn(List.of());
    }

    /** Gives the user these skills — one profile_skills row each, the only place skills live. */
    private void profileWith(String... skills) {
        when(profileSkillRepo.findByUserId(USER)).thenReturn(
                java.util.Arrays.stream(skills)
                        .map(name -> new ProfileSkill(UUID.randomUUID(), USER, name, null,
                                null, null, false, 0, null))
                        .toList());
    }

    private static Job posting(List<String> technologies, List<String> skills) {
        return Job.builder().id(UUID.randomUUID()).title("Role")
                .technologies(technologies).skills(skills).build();
    }

    private void market(Job... jobs) {
        when(marketCorpus.collect(any(), anyBoolean())).thenReturn(List.of(jobs));
    }

    @Test
    void candidatesAreRankedByHowManyMatchedPostingsAskForThem() {
        profileWith("Java");
        market(posting(List.of("Kubernetes", "Terraform"), List.of()),
               posting(List.of("Kubernetes"), List.of()),
               posting(List.of("Kubernetes"), List.of()));

        List<SkillCandidate> candidates = service.suggest(USER, 10);
        assertThat(candidates).extracting(SkillCandidate::name)
                .containsExactly("Kubernetes", "Terraform");
        assertThat(candidates.getFirst().marketFrequency()).isEqualTo(3);
    }

    @Test
    void aSkillTheUserAlreadyHasIsNeverSuggested() {
        profileWith("Java", "Kubernetes");
        market(posting(List.of("Java", "Kubernetes", "Terraform"), List.of()));

        assertThat(service.suggest(USER, 10)).extracting(SkillCandidate::name)
                .containsExactly("Terraform");
    }

    @Test
    void aPostingNamingTheSameSkillTwiceStillCountsOnce() {
        profileWith("Java");
        market(posting(List.of("Kubernetes"), List.of("Kubernetes")));
        assertThat(service.suggest(USER, 10).getFirst().marketFrequency()).isEqualTo(1);
    }

    @Test
    void taxonomyNeighboursAreOfferedAndCarryWhatTheyRelateTo() {
        profileWith("Spring Boot");
        UUID springBootId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        when(taxonomyRepo.findByNormalizedName("spring boot")).thenReturn(Optional.of(
                new SkillTaxonomy(springBootId, "Spring Boot", "spring boot", parentId, "Framework", List.of())));
        when(taxonomyRepo.findByParentIds(any())).thenReturn(List.of(
                new SkillTaxonomy(UUID.randomUUID(), "Hibernate", "hibernate", parentId, "Framework", List.of())));

        List<SkillCandidate> candidates = service.suggest(USER, 10);
        assertThat(candidates).singleElement().satisfies(c -> {
            assertThat(c.name()).isEqualTo("Hibernate");
            assertThat(c.relatedSkills()).containsExactly("Spring Boot");
            assertThat(c.source()).isEqualTo(SkillCandidate.SkillCandidateSource.TAXONOMY_ADJACENT);
        });
    }

    @Test
    void aCandidateThatIsBothAdjacentAndInDemandOutranksOneThatIsOnlyAdjacent() {
        profileWith("Spring Boot");
        UUID parentId = UUID.randomUUID();
        when(taxonomyRepo.findByNormalizedName("spring boot")).thenReturn(Optional.of(
                new SkillTaxonomy(UUID.randomUUID(), "Spring Boot", "spring boot", parentId, "Framework", List.of())));
        when(taxonomyRepo.findByParentIds(any())).thenReturn(List.of(
                new SkillTaxonomy(UUID.randomUUID(), "Hibernate", "hibernate", parentId, "Framework", List.of()),
                new SkillTaxonomy(UUID.randomUUID(), "Struts", "struts", parentId, "Framework", List.of())));
        market(posting(List.of("Hibernate"), List.of()));

        List<SkillCandidate> candidates = service.suggest(USER, 10);
        assertThat(candidates.getFirst().name()).isEqualTo("Hibernate");
        assertThat(candidates.getFirst().source()).isEqualTo(SkillCandidate.SkillCandidateSource.BOTH);
    }

    @Test
    void suggestionsWithNoMarketSignalAreCappedSoEveryRowHasAReason() {
        profileWith("Spring Boot");
        UUID parentId = UUID.randomUUID();
        when(taxonomyRepo.findByNormalizedName("spring boot")).thenReturn(Optional.of(
                new SkillTaxonomy(UUID.randomUUID(), "Spring Boot", "spring boot", parentId, "F", List.of())));
        List<SkillTaxonomy> manyNeighbours = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            manyNeighbours.add(new SkillTaxonomy(UUID.randomUUID(), "Neighbour " + i,
                    "neighbour " + i, parentId, "F", List.of()));
        }
        when(taxonomyRepo.findByParentIds(any())).thenReturn(manyNeighbours);

        assertThat(service.suggest(USER, 20)).hasSize(5);
    }

    @Test
    void decliningASkillRemovesItForGood() {
        profileWith("Java");
        market(posting(List.of("Kubernetes"), List.of()));
        assertThat(service.suggest(USER, 10)).isNotEmpty();

        service.confirm(USER, List.of(new SkillConfirmation("Kubernetes",
                SkillConfirmation.Decision.NO, false, null)));

        assertThat(service.suggest(USER, 10)).isEmpty();
    }

    @Test
    void acceptingASkillAddsItWithWhatTheUserSaidAboutIt() {
        profileWith("Java");
        when(profileSkillRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<ProfileSkill> added = service.confirm(USER, List.of(new SkillConfirmation(
                "Kubernetes", SkillConfirmation.Decision.YES, true, 3)));

        assertThat(added).singleElement().satisfies(skill -> {
            assertThat(skill.skillName()).isEqualTo("Kubernetes");
            assertThat(skill.usedInProduction()).isTrue();
            assertThat(skill.yearsExperience()).isEqualTo(3);
            assertThat(skill.userId()).isEqualTo(USER);
        });
    }

    @Test
    void skippingChangesNothingSoItComesBackNextTime() {
        profileWith("Java");
        market(posting(List.of("Kubernetes"), List.of()));
        service.confirm(USER, List.of(new SkillConfirmation("Kubernetes",
                SkillConfirmation.Decision.SKIP, false, null)));
        assertThat(service.suggest(USER, 10)).extracting(SkillCandidate::name).contains("Kubernetes");
    }

    @Test
    void confirmingSomethingAlreadyOnTheProfileDoesNotDuplicateIt() {
        profileWith("Java");
        List<ProfileSkill> added = service.confirm(USER, List.of(new SkillConfirmation(
                "java", SkillConfirmation.Decision.YES, true, null)));
        assertThat(added).isEmpty();
        Mockito.verify(profileSkillRepo, Mockito.never()).save(any());
    }

    @Test
    void anEmptyProfileAndAnEmptyMarketYieldNothingRatherThanEverything() {
        assertThat(service.suggest(USER, 10)).isEmpty();
    }

    // ── document-inferred candidates ──────────────────────────────────────────
    //
    // The parsers stay extractive — what they write to the profile is what the fact guard treats
    // as the candidate's own account. A skill their CV evidences without naming arrives here
    // instead, as a question, so a wrong reading costs a dismissal rather than a fabricated skill.

    @Test
    void aSkillTheUsersOwnCvEvidencesIsOfferedWithTheLineThatImpliedIt() {
        queueInferred("Scrum", "Ran fortnightly retrospectives and groomed the backlog");

        List<SkillCandidate> candidates = service.suggest(USER, 10);

        assertThat(candidates).extracting(SkillCandidate::name).contains("Scrum");
        SkillCandidate scrum = candidates.stream().filter(c -> c.name().equals("Scrum")).findFirst().orElseThrow();
        assertThat(scrum.source()).isEqualTo(SkillCandidate.SkillCandidateSource.DOCUMENT_INFERRED);
        assertThat(scrum.evidence()).isEqualTo("Ran fortnightly retrospectives and groomed the backlog");
    }

    @Test
    void aDocumentInferenceOutranksSomethingMerelyInDemand() {
        queueInferred("Scrum", "Ran fortnightly retrospectives");
        market(posting(List.of("Kubernetes"), List.of()),
               posting(List.of("Kubernetes"), List.of()),
               posting(List.of("Kubernetes"), List.of()));

        assertThat(service.suggest(USER, 10)).extracting(SkillCandidate::name).startsWith("Scrum");
    }

    @Test
    void aDocumentInferenceSurvivesTheNoDemandCap() {
        // Six market-less taxonomy candidates would push a seventh past MAX_UNDEMANDED_CANDIDATES;
        // an inference is never part of that tail, because it carries its own explanation.
        queueInferred("Scrum", "Ran fortnightly retrospectives");
        profileWith("Java");
        when(taxonomyRepo.findByNormalizedName("java")).thenReturn(Optional.of(
                new SkillTaxonomy(UUID.randomUUID(), "Java", "java", null, "Programming Language", List.of())));

        assertThat(service.suggest(USER, 10)).extracting(SkillCandidate::name).contains("Scrum");
    }

    @Test
    void anInferenceForSomethingTheUserAlreadyClaimsIsNotOffered() {
        profileWith("Scrum");
        queueInferred("Scrum", "Ran fortnightly retrospectives");

        assertThat(service.suggest(USER, 10)).extracting(SkillCandidate::name).doesNotContain("Scrum");
    }

    @Test
    void answeringAnInferenceTakesItOutOfTheQueueWhicheverWayItIsAnswered() {
        queueInferred("Scrum", "Ran fortnightly retrospectives");
        when(profileSkillRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.confirm(USER, List.of(new SkillConfirmation("Scrum",
                SkillConfirmation.Decision.YES, true, null)));
        assertThat(parsedSuggestions).isEmpty();

        queueInferred("Kanban", "Managed a WIP-limited board");
        service.confirm(USER, List.of(new SkillConfirmation("Kanban",
                SkillConfirmation.Decision.NO, false, null)));
        assertThat(parsedSuggestions).isEmpty();
        assertThat(service.suggest(USER, 10)).extracting(SkillCandidate::name).doesNotContain("Kanban");
    }

    @Test
    void aSkippedInferenceStaysQueuedForNextTime() {
        queueInferred("Scrum", "Ran fortnightly retrospectives");

        service.confirm(USER, List.of(new SkillConfirmation("Scrum",
                SkillConfirmation.Decision.SKIP, false, null)));

        assertThat(service.suggest(USER, 10)).extracting(SkillCandidate::name).contains("Scrum");
    }

    private void queueInferred(String name, String evidence) {
        parsedSuggestionRepo.record(new ParsedSkillSuggestion(UUID.randomUUID(), USER, name,
                name.toLowerCase(java.util.Locale.ROOT), evidence,
                ParsedSkillSuggestion.Source.CV_PARSE));
    }
}
