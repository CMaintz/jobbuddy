package com.autoapplicant.usecase.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.domain.skill.SkillCandidate;
import com.autoapplicant.domain.skill.SkillConfirmation;
import com.autoapplicant.domain.skill.SkillTaxonomy;
import com.autoapplicant.port.out.skills.ProfileSkillRepositoryPort;
import com.autoapplicant.port.out.skills.SkillTaxonomyRepositoryPort;
import com.autoapplicant.usecase.job.MarketCorpusService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SkillCandidateServiceTest extends SkillCandidateServiceFixture {

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
    void aSkillHeldUnderTheMasterSpellingIsNotReSuggestedUnderAnAlias() {
        // Own mocks so the canonicalizer sees the k8s->Kubernetes alias (the shared instance caches
        // an empty taxonomy). The user holds Kubernetes; a posting asks for "k8s".
        SkillTaxonomy kube = new SkillTaxonomy(
                UUID.randomUUID(), "Kubernetes", "kubernetes", null, "DevOps", List.of("k8s"));
        SkillTaxonomyRepositoryPort tax = Mockito.mock(SkillTaxonomyRepositoryPort.class);
        when(tax.findAll()).thenReturn(List.of(kube));
        when(tax.findByNormalizedName(any())).thenReturn(Optional.empty());
        when(tax.findByParentIds(any())).thenReturn(List.of());
        ProfileSkillRepositoryPort skills = Mockito.mock(ProfileSkillRepositoryPort.class);
        when(skills.findByUserId(USER)).thenReturn(List.of(new ProfileSkill(
                UUID.randomUUID(), USER, "Kubernetes", kube.id(), null, null, false, 0, "DevOps")));
        MarketCorpusService market = Mockito.mock(MarketCorpusService.class);
        when(market.collect(any(), anyBoolean())).thenReturn(List.of(
                Job.builder().id(UUID.randomUUID()).title("Role")
                        .technologies(List.of("k8s")).skills(List.of()).build()));
        SkillCandidateService svc = new SkillCandidateService(skills, tax, dismissalRepo, market,
                parsedSuggestionRepo, new SkillResolver(tax, new SkillCanonicalizer(tax)));

        assertThat(svc.suggest(USER, 10)).extracting(SkillCandidate::name)
                .doesNotContain("k8s", "Kubernetes");
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
        marketlessNeighbours(20);

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
}
