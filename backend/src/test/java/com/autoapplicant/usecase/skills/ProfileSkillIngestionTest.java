package com.autoapplicant.usecase.skills;

import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.domain.skill.SkillTaxonomy;
import com.autoapplicant.port.out.skills.ProfileSkillRepositoryPort;
import com.autoapplicant.port.out.skills.SkillTaxonomyRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

/**
 * How an imported CV's skills reach the profile.
 *
 * <p>They used to land in a text[] column while hand-typed skills became structured rows, so a
 * skill's category, taxonomy link and proficiency depended on how it had arrived. Anyone who
 * onboarded by uploading a CV got none of them, and every feature built on those attributes
 * quietly did nothing for them.
 */
@ExtendWith(MockitoExtension.class)
class ProfileSkillIngestionTest {

    @Mock ProfileSkillRepositoryPort skillRepo;
    @Mock SkillTaxonomyRepositoryPort taxonomyRepo;

    ProfileSkillIngestion ingestion;
    final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ingestion = new ProfileSkillIngestion(skillRepo,
                new SkillResolver(taxonomyRepo, new SkillCanonicalizer(taxonomyRepo)));
        lenient().when(skillRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(taxonomyRepo.findByNormalizedNames(anyCollection())).thenReturn(List.of());
    }

    @Test
    void an_imported_skill_arrives_taxonomy_linked_and_categorised() {
        UUID taxonomyId = UUID.randomUUID();
        when(skillRepo.findByUserId(userId)).thenReturn(List.of());
        when(taxonomyRepo.findByNormalizedNames(anyCollection())).thenReturn(List.of(
                new SkillTaxonomy(taxonomyId, "Kubernetes", "kubernetes", null, "DevOps", null)));

        List<ProfileSkill> result = ingestion.ingest(userId, List.of("Kubernetes"));

        assertThat(result).singleElement().satisfies(s -> {
            assertThat(s.skillName()).isEqualTo("Kubernetes");
            assertThat(s.taxonomyId()).isEqualTo(taxonomyId);
            assertThat(s.category()).isEqualTo("DevOps");
        });
    }

    @Test
    void a_skill_the_taxonomy_does_not_know_is_still_kept() {
        // A real skill the seed list has never heard of is not a reason to drop it.
        when(skillRepo.findByUserId(userId)).thenReturn(List.of());

        List<ProfileSkill> result = ingestion.ingest(userId, List.of("Brandsikring"));

        assertThat(result).singleElement().satisfies(s -> {
            assertThat(s.skillName()).isEqualTo("Brandsikring");
            assertThat(s.taxonomyId()).isNull();
            assertThat(s.category()).isNull();
        });
    }

    @Test
    void re_importing_does_not_duplicate_or_reset_what_the_user_already_has() {
        ProfileSkill existing = new ProfileSkill(UUID.randomUUID(), userId, "Java", null,
                "EXPERT", 8, true, 0, "Programming Language");
        when(skillRepo.findByUserId(userId)).thenReturn(List.of(existing));

        List<ProfileSkill> result = ingestion.ingest(userId, List.of("java", "Kotlin"));

        // Java matched case-insensitively and was left exactly as recorded — an import must not
        // wipe the proficiency and years someone entered by hand.
        assertThat(result).contains(existing);
        assertThat(result).extracting(ProfileSkill::skillName)
                .containsExactlyInAnyOrder("Java", "Kotlin");
        verify(skillRepo, times(1)).save(any());
    }

    @Test
    void a_name_repeated_within_one_import_is_added_once() {
        when(skillRepo.findByUserId(userId)).thenReturn(List.of());

        ingestion.ingest(userId, List.of("Docker", "docker", " Docker "));

        verify(skillRepo, times(1)).save(any());
    }

    @Test
    void proficiency_is_left_unset_rather_than_guessed() {
        // The document says they have the skill. How deeply, only they can say.
        when(skillRepo.findByUserId(userId)).thenReturn(List.of());

        List<ProfileSkill> result = ingestion.ingest(userId, List.of("Terraform"));

        assertThat(result).singleElement().satisfies(s -> {
            assertThat(s.proficiencyLevel()).isNull();
            assertThat(s.yearsExperience()).isNull();
            assertThat(s.usedInProduction()).isFalse();
        });
    }

    @Test
    void an_imported_alias_is_stored_under_the_master_spelling() {
        SkillTaxonomy kube = new SkillTaxonomy(
                UUID.randomUUID(), "Kubernetes", "kubernetes", null, "DevOps", List.of("k8s"));
        when(taxonomyRepo.findAll()).thenReturn(List.of(kube));
        when(taxonomyRepo.findByNormalizedNames(anyCollection())).thenReturn(List.of(kube));
        when(skillRepo.findByUserId(userId)).thenReturn(List.of());

        List<ProfileSkill> result = ingestion.ingest(userId, List.of("k8s"));

        assertThat(result).singleElement().satisfies(s -> {
            assertThat(s.skillName()).isEqualTo("Kubernetes");
            assertThat(s.category()).isEqualTo("DevOps");
        });
    }

    @Test
    void an_imported_alias_of_a_held_skill_is_not_added_again() {
        SkillTaxonomy kube = new SkillTaxonomy(
                UUID.randomUUID(), "Kubernetes", "kubernetes", null, "DevOps", List.of("k8s"));
        when(taxonomyRepo.findAll()).thenReturn(List.of(kube));
        ProfileSkill held = new ProfileSkill(
                UUID.randomUUID(), userId, "Kubernetes", kube.id(), "EXPERT", 5, true, 0, "DevOps");
        when(skillRepo.findByUserId(userId)).thenReturn(List.of(held));

        List<ProfileSkill> result = ingestion.ingest(userId, List.of("k8s"));

        verify(skillRepo, never()).save(any());
        assertThat(result).containsExactly(held);
    }

    @Test
    void an_empty_import_leaves_the_profile_alone() {
        when(skillRepo.findByUserId(userId)).thenReturn(List.of());

        assertThat(ingestion.ingest(userId, List.of())).isEmpty();
        verify(skillRepo, never()).save(any());
    }
}
