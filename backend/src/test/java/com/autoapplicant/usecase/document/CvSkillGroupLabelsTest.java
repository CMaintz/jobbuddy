package com.autoapplicant.usecase.document;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The seam between the skill taxonomy and the finished CV.
 *
 * <p>Skill categories are stored as a classification vocabulary and rendered as CV headings
 * verbatim, so without a translation step a categorised profile prints "Programming Language:"
 * where a skill typed in the resume builder prints "Languages:" — on the same document.
 */
class CvSkillGroupLabelsTest {

    @Test
    void taxonomy_categories_become_headings_a_cv_would_actually_use() {
        CvSkillGroupLabels labels = CvSkillGroupLabels.forLanguage("en");

        assertThat(labels.labelFor("Programming Language")).isEqualTo("Programming Languages");
        assertThat(labels.labelFor("Framework")).isEqualTo("Frameworks");
        assertThat(labels.labelFor("Database")).isEqualTo("Databases");
        assertThat(labels.labelFor("Tool")).isEqualTo("Tools");
        assertThat(labels.labelFor("Methodology")).isEqualTo("Practices");
    }

    @Test
    void the_translated_headings_agree_with_the_resume_builders_own_suggestions() {
        // A skill categorised from the taxonomy and one typed into the builder must not produce
        // two headings that mean the same thing.
        CvSkillGroupLabels labels = CvSkillGroupLabels.forLanguage("en");

        assertThat(labels.labelFor("Programming Language")).isEqualTo("Programming Languages");
        assertThat(labels.labelFor("Cloud")).isEqualTo("Cloud");
        assertThat(labels.labelFor("Methodology")).isEqualTo("Practices");
    }

    @Test
    void the_programming_language_group_does_not_collide_with_the_spoken_languages_section() {
        // A CV carries a Languages section for the languages the candidate speaks. A skill group
        // also headed "Languages", listing Java, would put two of them on one page.
        for (String language : new String[]{"en", "da"}) {
            assertThat(CvSkillGroupLabels.forLanguage(language).labelFor("Programming Language"))
                    .isNotEqualTo(CvSectionLabels.forLanguage(language).languages());
        }
    }

    @Test
    void a_soft_skill_never_heads_a_group() {
        // The CV persona says soft skills are woven into the experience descriptions, never
        // listed. A "Soft Skill:" heading would make the document contradict its own prompt.
        assertThat(CvSkillGroupLabels.forLanguage("en").labelFor("Soft Skill")).isNull();
        assertThat(CvSkillGroupLabels.forLanguage("da").labelFor("soft skill")).isNull();
    }

    @Test
    void a_danish_cv_gets_danish_headings() {
        CvSkillGroupLabels labels = CvSkillGroupLabels.forLanguage("da");

        assertThat(labels.labelFor("Programming Language")).isEqualTo("Programmeringssprog");
        assertThat(labels.labelFor("Tool")).isEqualTo("Værktøjer");
        assertThat(labels.labelFor("Database")).isEqualTo("Databaser");
    }

    @Test
    void a_category_the_user_invented_is_kept_exactly_as_they_wrote_it() {
        // They chose that word for this CV; second-guessing it is worse than an odd heading.
        assertThat(CvSkillGroupLabels.forLanguage("en").labelFor("Broadcast Engineering"))
                .isEqualTo("Broadcast Engineering");
    }

    @Test
    void an_uncategorised_skill_stays_uncategorised() {
        CvSkillGroupLabels labels = CvSkillGroupLabels.forLanguage("en");

        assertThat(labels.labelFor(null)).isNull();
        assertThat(labels.labelFor("   ")).isNull();
    }
}
