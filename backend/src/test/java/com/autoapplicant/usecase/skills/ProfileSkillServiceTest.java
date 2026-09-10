package com.autoapplicant.usecase.skills;

import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.domain.skill.SkillTaxonomy;
import com.autoapplicant.port.out.skills.ProfileSkillRepositoryPort;
import com.autoapplicant.port.out.skills.SkillTaxonomyRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The categorisation contract. A skill's category decides which group it appears under and is
 * the only classification a skill outside the taxonomy will ever carry, so "typed rather than
 * picked from the autocomplete" must not mean "uncategorised forever".
 */
@ExtendWith(MockitoExtension.class)
class ProfileSkillServiceTest {

    @Mock ProfileSkillRepositoryPort repo;
    @Mock SkillTaxonomyRepositoryPort taxonomyRepo;

    ProfileSkillService service;
    final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProfileSkillService(repo,
                new SkillResolver(taxonomyRepo, new SkillCanonicalizer(taxonomyRepo)));
        lenient().when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void a_typed_skill_name_adopts_the_taxonomy_entry_and_its_category() {
        UUID taxonomyId = UUID.randomUUID();
        when(taxonomyRepo.findByNormalizedName("kubernetes")).thenReturn(Optional.of(
                new SkillTaxonomy(taxonomyId, "Kubernetes", "kubernetes", null, "DevOps", null)));

        service.addSkill(skill("  Kubernetes ", null, null));

        ProfileSkill saved = captureSaved();
        assertThat(saved.taxonomyId()).isEqualTo(taxonomyId);
        assertThat(saved.category()).isEqualTo("DevOps");
    }

    @Test
    void a_category_the_caller_chose_is_not_overruled_by_the_taxonomy() {
        when(taxonomyRepo.findByNormalizedName("kubernetes")).thenReturn(Optional.of(
                new SkillTaxonomy(UUID.randomUUID(), "Kubernetes", "kubernetes", null, "DevOps", null)));

        service.addSkill(skill("Kubernetes", null, "Tool"));

        assertThat(captureSaved().category()).isEqualTo("Tool");
    }

    @Test
    void a_skill_outside_the_taxonomy_keeps_the_category_it_arrived_with() {
        when(taxonomyRepo.findByNormalizedName("brandsikring")).thenReturn(Optional.empty());

        service.addSkill(skill("Brandsikring", null, "Domain"));

        ProfileSkill saved = captureSaved();
        assertThat(saved.taxonomyId()).isNull();
        assertThat(saved.category()).isEqualTo("Domain");
    }

    @Test
    void a_skill_that_already_knows_its_taxonomy_and_category_does_not_hit_the_taxonomy_at_all() {
        service.addSkill(skill("Kubernetes", UUID.randomUUID(), "DevOps"));

        verifyNoInteractions(taxonomyRepo);
    }

    @Test
    void updating_a_skill_resolves_its_category_the_same_way_adding_one_does() {
        when(taxonomyRepo.findByNormalizedName("java")).thenReturn(Optional.of(
                new SkillTaxonomy(UUID.randomUUID(), "Java", "java", null, "Programming Language", null)));

        service.updateSkill(skill("Java", null, null));

        assertThat(captureSaved().category()).isEqualTo("Programming Language");
    }

    @Test
    void an_alias_is_collapsed_onto_its_master_spelling() {
        UUID taxonomyId = UUID.randomUUID();
        SkillTaxonomy kube = new SkillTaxonomy(taxonomyId, "Kubernetes", "kubernetes", null, "DevOps", List.of("k8s"));
        when(taxonomyRepo.findAll()).thenReturn(List.of(kube));
        when(taxonomyRepo.findByNormalizedName("kubernetes")).thenReturn(Optional.of(kube));

        service.addSkill(skill("k8s", null, null));

        ProfileSkill saved = captureSaved();
        assertThat(saved.skillName()).isEqualTo("Kubernetes");   // stored under the master's spelling
        assertThat(saved.taxonomyId()).isEqualTo(taxonomyId);
        assertThat(saved.category()).isEqualTo("DevOps");
    }

    @Test
    void adding_an_alias_of_a_held_skill_merges_into_it_rather_than_duplicating() {
        UUID taxonomyId = UUID.randomUUID();
        SkillTaxonomy kube = new SkillTaxonomy(taxonomyId, "Kubernetes", "kubernetes", null, "DevOps", List.of("k8s"));
        when(taxonomyRepo.findAll()).thenReturn(List.of(kube));
        when(taxonomyRepo.findByNormalizedName("kubernetes")).thenReturn(Optional.of(kube));
        UUID existingId = UUID.randomUUID();
        when(repo.findByUserId(userId)).thenReturn(List.of(new ProfileSkill(
                existingId, userId, "Kubernetes", taxonomyId, "INTERMEDIATE", null, false, 0, "DevOps")));

        // Same skill, arriving as its alias with fresh detail the user supplied.
        service.addSkill(new ProfileSkill(null, userId, "k8s", null, "ADVANCED", 3, true, 0, null));

        ProfileSkill saved = captureSaved();
        assertThat(saved.id()).isEqualTo(existingId);            // merged into the existing row
        assertThat(saved.skillName()).isEqualTo("Kubernetes");
        assertThat(saved.proficiencyLevel()).isEqualTo("ADVANCED");
        assertThat(saved.yearsExperience()).isEqualTo(3);
        assertThat(saved.usedInProduction()).isTrue();
    }

    private ProfileSkill captureSaved() {
        ArgumentCaptor<ProfileSkill> captor = ArgumentCaptor.forClass(ProfileSkill.class);
        verify(repo).save(captor.capture());
        return captor.getValue();
    }

    private ProfileSkill skill(String name, UUID taxonomyId, String category) {
        return new ProfileSkill(null, userId, name, taxonomyId,
                "INTERMEDIATE", null, false, 0, category);
    }
}
