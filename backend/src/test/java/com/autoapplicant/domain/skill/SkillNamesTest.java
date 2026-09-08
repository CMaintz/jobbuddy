package com.autoapplicant.domain.skill;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Skill names are keys, and the punctuation in them is load-bearing.
 *
 * <p>A slugifying normalisation once lived alongside this one and folded punctuation to hyphens,
 * which made C# and C++ the same key. These tests pin the names that break under any scheme that
 * treats punctuation as noise.
 */
class SkillNamesTest {

    @Test
    void punctuated_skill_names_stay_distinct() {
        assertThat(SkillNames.normalize("C#")).isNotEqualTo(SkillNames.normalize("C++"));
        assertThat(SkillNames.normalize(".NET")).isNotEqualTo(SkillNames.normalize("Node.js"));
        assertThat(SkillNames.normalize("CI/CD")).isNotEqualTo(SkillNames.normalize("Vue.js"));
    }

    @Test
    void the_key_matches_how_the_taxonomy_is_seeded() {
        // V021 seeds these normalized_name values verbatim; a lookup that does not produce them
        // creates a duplicate row instead of finding the seeded one.
        assertThat(SkillNames.normalize("C#")).isEqualTo("c#");
        assertThat(SkillNames.normalize("C++")).isEqualTo("c++");
        assertThat(SkillNames.normalize(".NET")).isEqualTo(".net");
        assertThat(SkillNames.normalize("Node.js")).isEqualTo("node.js");
        assertThat(SkillNames.normalize("CI/CD")).isEqualTo("ci/cd");
        assertThat(SkillNames.normalize("Machine Learning")).isEqualTo("machine learning");
    }

    @Test
    void case_and_surrounding_space_do_not_make_a_different_skill() {
        assertThat(SkillNames.normalize("  KuBeRnEtEs \n")).isEqualTo("kubernetes");
    }

    @Test
    void a_missing_name_keys_to_nothing_rather_than_throwing() {
        assertThat(SkillNames.normalize(null)).isEmpty();
        assertThat(SkillNames.normalize("   ")).isEmpty();
    }
}
