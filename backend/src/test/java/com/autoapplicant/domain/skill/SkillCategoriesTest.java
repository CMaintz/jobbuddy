package com.autoapplicant.domain.skill;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The skills-versus-technologies split used to be two stored columns. It is derived from the
 * taxonomy category now, so this is where that meaning lives.
 */
class SkillCategoriesTest {

    @Test
    void tool_and_platform_categories_count_as_technologies() {
        assertThat(SkillCategories.isTechnical("Programming Language")).isTrue();
        assertThat(SkillCategories.isTechnical("Framework")).isTrue();
        assertThat(SkillCategories.isTechnical("Cloud")).isTrue();
        assertThat(SkillCategories.isTechnical("DevOps")).isTrue();
        assertThat(SkillCategories.isTechnical("AI/ML")).isTrue();
    }

    @Test
    void ways_of_working_are_not_technologies() {
        assertThat(SkillCategories.isTechnical("Soft Skill")).isFalse();
        assertThat(SkillCategories.isTechnical("Methodology")).isFalse();
        assertThat(SkillCategories.isTechnical("Domain")).isFalse();
    }

    @Test
    void an_uncategorised_skill_is_not_assumed_technical() {
        // Guessing here would put a hand-typed skill in the wrong CV group.
        assertThat(SkillCategories.isTechnical(null)).isFalse();
        assertThat(SkillCategories.isTechnical("")).isFalse();
        assertThat(SkillCategories.isTechnical("Broadcast Engineering")).isFalse();
    }

    @Test
    void category_matching_ignores_case_and_padding() {
        assertThat(SkillCategories.isTechnical("  programming language ")).isTrue();
        assertThat(SkillCategories.isSoftSkill(" SOFT SKILL ")).isTrue();
    }
}
