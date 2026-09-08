package com.autoapplicant.usecase.skills;

import com.autoapplicant.domain.skill.SkillTaxonomy;
import com.autoapplicant.port.out.skills.SkillTaxonomyRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * createOrGet has to agree with every read path about what a skill name keys to, or it creates a
 * second row for a skill the taxonomy already knows.
 */
@ExtendWith(MockitoExtension.class)
class SkillTaxonomyServiceTest {

    @Mock SkillTaxonomyRepositoryPort repo;

    @Test
    void a_punctuated_name_finds_its_seeded_row_instead_of_duplicating_it() {
        UUID seeded = UUID.randomUUID();
        when(repo.findByNormalizedName("c#")).thenReturn(Optional.of(
                new SkillTaxonomy(seeded, "C#", "c#", null, "Programming Language", null)));

        SkillTaxonomy result = new SkillTaxonomyService(repo).createOrGet("C#", null);

        assertThat(result.id()).isEqualTo(seeded);
        verify(repo, never()).save(any());
    }

    @Test
    void two_skills_that_differ_only_in_punctuation_do_not_collide() {
        // Under the old slug they both keyed to "c-", so the second call returned the first row.
        when(repo.findByNormalizedName("c++")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SkillTaxonomy cpp = new SkillTaxonomyService(repo).createOrGet("C++", "Programming Language");

        assertThat(cpp.normalizedName()).isEqualTo("c++");
        verify(repo).findByNormalizedName("c++");
        verify(repo, never()).findByNormalizedName("c-");
    }

    @Test
    void an_unknown_skill_is_created_under_the_chosen_category() {
        when(repo.findByNormalizedName("brandsikring")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SkillTaxonomy created = new SkillTaxonomyService(repo).createOrGet(" Brandsikring ", "Domain");

        assertThat(created.name()).isEqualTo("Brandsikring");
        assertThat(created.normalizedName()).isEqualTo("brandsikring");
        assertThat(created.category()).isEqualTo("Domain");
    }
}
