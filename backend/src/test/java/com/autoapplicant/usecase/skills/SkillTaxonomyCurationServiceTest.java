package com.autoapplicant.usecase.skills;

import com.autoapplicant.domain.skill.SkillMention;
import com.autoapplicant.domain.skill.SkillTaxonomy;
import com.autoapplicant.domain.skill.TaxonomyCandidate;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.skills.SkillTaxonomyRepositoryPort;
import com.autoapplicant.port.out.skills.TaxonomyRejectionRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillTaxonomyCurationServiceTest {

    private final JobRepositoryPort jobRepo = Mockito.mock(JobRepositoryPort.class);
    private final SkillTaxonomyRepositoryPort taxonomyRepo = Mockito.mock(SkillTaxonomyRepositoryPort.class);

    /** Real rejection store: "never offer this again" is the one thing the queue remembers. */
    private final Set<String> rejected = new HashSet<>();
    private final List<String> rejectedLabels = new ArrayList<>();
    private final TaxonomyRejectionRepositoryPort rejectionRepo = new TaxonomyRejectionRepositoryPort() {
        @Override public Set<String> findRejectedNormalizedNames() { return rejected; }
        @Override public void reject(String normalizedName, String label, String reason) {
            rejected.add(normalizedName);
            rejectedLabels.add(label);
        }
    };

    private final SkillTaxonomyCurationService service =
            new SkillTaxonomyCurationService(jobRepo, taxonomyRepo, rejectionRepo);

    @BeforeEach
    void setUp() {
        when(taxonomyRepo.findAllKnownNormalizedNames()).thenReturn(Set.of());
        when(jobRepo.findSkillMentions(anyInt())).thenReturn(List.of());
    }

    private void market(SkillMention... mentions) {
        when(jobRepo.findSkillMentions(anyInt())).thenReturn(List.of(mentions));
    }

    @Test
    void surfacesWhatTheMarketNamesAndTheTaxonomyDoesNot() {
        when(taxonomyRepo.findAllKnownNormalizedNames()).thenReturn(Set.of("java"));
        market(new SkillMention("Java", 40, 40),
               new SkillMention("Rust", 12, 12),
               new SkillMention("Stakeholder Management", 7, 0));

        assertThat(service.candidates(10))
                .extracting(TaxonomyCandidate::name)
                .containsExactly("Rust", "Stakeholder Management");
    }

    @Test
    void anAliasOfAKnownSkillIsNotAGap() {
        // The port hands back aliases alongside names precisely so "K8s" does not become a
        // second row for a skill the taxonomy already has.
        when(taxonomyRepo.findAllKnownNormalizedNames()).thenReturn(Set.of("kubernetes", "k8s"));
        market(new SkillMention("K8s", 15, 15));

        assertThat(service.candidates(10)).isEmpty();
    }

    @Test
    void rejectedLabelsStayOut() {
        market(new SkillMention("Kaffe", 9, 0));
        assertThat(service.candidates(10)).extracting(TaxonomyCandidate::name).containsExactly("Kaffe");

        service.reject("kaffe ", "Not a skill");

        assertThat(service.candidates(10)).isEmpty();
        // Keyed the way every other skill lookup keys it, so the casing of the next posting
        // that names it does not matter.
        assertThat(rejected).containsExactly("kaffe");
        assertThat(rejectedLabels).containsExactly("kaffe");
    }

    @Test
    void oneMentionIsATypoRatherThanAGap() {
        market(new SkillMention("Javva", 1, 1), new SkillMention("Rust", 2, 2));

        assertThat(service.candidates(10)).extracting(TaxonomyCandidate::name).containsExactly("Rust");
    }

    @Test
    void rankedByHowManyPostingsNameIt() {
        market(new SkillMention("Terraform", 5, 5),
               new SkillMention("Rust", 30, 30),
               new SkillMention("Ansible", 5, 5));

        assertThat(service.candidates(10)).extracting(TaxonomyCandidate::name)
                // Ties break on the name, so the list does not reshuffle between visits.
                .containsExactly("Rust", "Ansible", "Terraform");
    }

    @Test
    void reportsWhichDrawerEnrichmentKeptPuttingItIn() {
        market(new SkillMention("Rust", 10, 10), new SkillMention("Stakeholder Management", 10, 1));

        assertThat(service.candidates(10))
                .extracting(TaxonomyCandidate::name, TaxonomyCandidate::readAsTechnology)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Rust", true),
                        org.assertj.core.groups.Tuple.tuple("Stakeholder Management", false));
    }

    @Test
    void honoursTheLimitAndAsksForNothingWhenNoneIsWanted() {
        market(new SkillMention("Rust", 30, 30), new SkillMention("Zig", 20, 20));

        assertThat(service.candidates(1)).extracting(TaxonomyCandidate::name).containsExactly("Rust");
        assertThat(service.candidates(0)).isEmpty();
        verify(jobRepo, never()).findSkillMentions(0);
    }

    // ── Approval ──────────────────────────────────────────────────────────────────────────

    @Test
    void approvingAddsTheRowUnderTheChosenCategory() {
        when(taxonomyRepo.findByNormalizedName("rust")).thenReturn(Optional.empty());
        when(taxonomyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SkillTaxonomy saved = service.approve(" Rust ", "Programming Language");

        assertThat(saved.name()).isEqualTo("Rust");
        assertThat(saved.normalizedName()).isEqualTo("rust");
        assertThat(saved.category()).isEqualTo("Programming Language");
    }

    @Test
    void aCategoryIsNeverGuessed() {
        // The category decides how a CV groups the skill and whether it counts as a technology.
        // Defaulting it would write a wrong answer into data every user inherits.
        assertThatThrownBy(() -> service.approve("Rust", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.approve("Rust", "  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.approve(" ", "Tool"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(taxonomyRepo, never()).save(any());
    }

    @Test
    void approvingSomethingAlreadyThereChangesNothing() {
        SkillTaxonomy existing = new SkillTaxonomy(java.util.UUID.randomUUID(), "Rust", "rust",
                null, "Programming Language", List.of());
        when(taxonomyRepo.findByNormalizedName("rust")).thenReturn(Optional.of(existing));

        assertThat(service.approve("RUST", "Tool")).isEqualTo(existing);
        verify(taxonomyRepo, never()).save(any());
    }
}
