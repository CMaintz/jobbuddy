package com.autoapplicant.usecase.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.port.out.skills.SkillTaxonomyRepositoryPort;
import com.autoapplicant.usecase.skills.SkillCanonicalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Personality traits a posting lists next to its skills ("Struktur", "Team player"). */
class PersonalTraitAsksTest {

    private static final String HELD = "Tekstforfatning";

    private final SkillCanonicalizer canonicalizer =
            new SkillCanonicalizer(mock(SkillTaxonomyRepositoryPort.class));

    @ParameterizedTest
    @ValueSource(strings = {"Samarbejde", "Struktureret", "Selvstændig", "Ansvarsbevidst",
            "Team player", "Self-motivated"})
    void a_trait_ask_is_not_a_missing_skill(String trait) {
        SkillFitScorer scorer = scorer(HELD);

        assertThat(scorer.score(required(HELD, trait), new ArrayList<>()))
                .isEqualTo(scorer.score(required(HELD), new ArrayList<>()));
    }

    @Test
    void a_trait_the_profile_lists_is_no_evidence_of_fit() {
        SkillFitScorer scorer = scorer(HELD, "Samarbejde");

        assertThat(scorer.score(required("Samarbejde", "Bogholderi"), new ArrayList<>()))
                .isEqualTo(scorer.score(required("Bogholderi"), new ArrayList<>()));
    }

    /** "Engagement" and "Ownership" are skills in marketing and product postings, not traits. */
    @ParameterizedTest
    @ValueSource(strings = {"Forældresamarbejde", "Engagement", "Ownership"})
    void a_skill_named_like_a_trait_still_counts_as_missing(String skill) {
        SkillFitScorer scorer = scorer(HELD);

        assertThat(scorer.score(required(HELD, skill), new ArrayList<>()))
                .isEqualTo(scorer.score(required(HELD, "Bogholderi"), new ArrayList<>()));
    }

    @Test
    void a_trait_is_not_reported_as_a_missing_requirement() {
        List<String> reasons = new ArrayList<>();

        scorer(HELD).score(required(HELD, "Struktur", "Korrekturlæsning"), reasons);

        assertThat(reasons).contains("1/2 required skills covered",
                "Missing required: Korrekturlæsning");
    }

    private SkillFitScorer scorer(String... held) {
        UUID userId = UUID.randomUUID();
        Profile profile = new Profile(UUID.randomUUID(), userId, null, null, null, List.of(),
                List.of(), null, null, null, null, null, null, null);
        List<ProfileSkill> skills = List.of(held).stream().map(name -> new ProfileSkill(
                UUID.randomUUID(), userId, name, null, null, null, false, 0, null)).toList();
        return SkillFitScorer.forProfile(canonicalizer, profile, skills, List.of(),
                SkillSimilarity.NONE);
    }

    private static Job required(String... asks) {
        return Job.builder().id(UUID.randomUUID()).title("Opslag")
                .requiredSkills(List.of(asks)).build();
    }
}
