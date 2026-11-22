package com.autoapplicant.usecase.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.port.out.skills.SkillTaxonomyRepositoryPort;
import com.autoapplicant.usecase.skills.SkillCanonicalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Credit for asks a profile answers by meaning or by a suggestion it has not confirmed. */
class AdjacentSkillCreditTest {

    private static final String HELD = "Risikovurdering";
    /** What a posting the profile shares nothing with can earn from loose credit alone. */
    private static final int LOOSE_ONLY_CEILING = 22;

    private final SkillCanonicalizer canonicalizer =
            new SkillCanonicalizer(mock(SkillTaxonomyRepositoryPort.class));
    private final Map<String, Double> similarities = new HashMap<>();
    private final SkillSimilarity similarity =
            (ask, skill) -> similarities.getOrDefault(ask + "|" + skill, 0.0);

    @Test
    void an_ask_close_in_meaning_to_a_held_skill_earns_credit_and_a_distant_one_none() {
        similar("Risikostyring", HELD, 0.78);
        similar("Grafisk design", HELD, 0.45);

        int close = score(List.of(), HELD, "Risikostyring");
        int distant = score(List.of(), HELD, "Grafisk design");

        assertThat(close).isGreaterThan(distant);
        assertThat(distant).isEqualTo(score(List.of(), HELD, "Ukendt"));
    }

    @Test
    void asks_close_in_meaning_cannot_make_a_fit_on_their_own() {
        List<String> asks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            asks.add("Nærliggende " + i);
            similar("Nærliggende " + i, "Pressearbejde", 0.9);
        }

        int fit = scorer(List.of("Pressearbejde"), List.of())
                .score(required(asks.toArray(String[]::new)), new ArrayList<>());

        assertThat(fit).isLessThanOrEqualTo(LOOSE_ONLY_CEILING);
    }

    @Test
    void a_fit_its_own_skills_show_keeps_gaining_from_asks_it_answers_by_meaning() {
        for (String ask : List.of("Risikostyring", "Risikoanalyse", "Risikohåndtering")) {
            similar(ask, HELD, 0.9);
        }

        int threeAdjacent = scorer(List.of(HELD), List.of()).score(
                required(HELD, "Risikostyring", "Risikoanalyse", "Risikohåndtering"),
                new ArrayList<>());
        int twoAdjacent = scorer(List.of(HELD), List.of()).score(
                required(HELD, "Risikostyring", "Risikoanalyse"), new ArrayList<>());

        assertThat(threeAdjacent).isGreaterThan(twoAdjacent);
    }

    @Test
    void an_unconfirmed_suggestion_earns_less_than_the_same_skill_held() {
        int suggested = score(List.of("Content marketing"), HELD, "Content marketing");
        int confirmed = scorer(List.of(HELD, "Content marketing"), List.of())
                .score(required(HELD, "Content marketing"), new ArrayList<>());
        int neither = score(List.of(), HELD, "Content marketing");

        assertThat(suggested).isGreaterThan(neither).isLessThan(confirmed);
    }

    @Test
    void a_suggested_skill_is_never_reported_as_covered() {
        List<String> reasons = new ArrayList<>();

        scorer(List.of(HELD), List.of("Content marketing"))
                .score(required("Content marketing"), reasons);

        assertThat(reasons).contains("0/1 required skill covered",
                "Missing required: Content marketing");
    }

    @Test
    void a_faint_likeness_does_not_wipe_a_missing_required_ask() {
        similar("Grafisk design", HELD, LooseCredit.SIMILAR_FROM + 0.01);

        int faint = score(List.of(), HELD, "Grafisk design");
        int none = score(List.of(), HELD, "Ukendt");

        assertThat(faint - none).isLessThanOrEqualTo(1);
    }

    @Test
    void labels_sharing_a_generic_head_earn_nothing_by_meaning() {
        similar("Leverandørvurdering", HELD, 0.72);

        assertThat(score(List.of(), HELD, "Leverandørvurdering"))
                .isEqualTo(score(List.of(), HELD, "Ukendt"));
    }

    /**
     * "Arbejde", "analyse" and "service" end too many real Danish skills to be generic as a
     * compound's last part: press work and media work are close, and so are their embeddings.
     */
    @ParameterizedTest
    @CsvSource({"Mediearbejde, Pressearbejde", "Markedsanalyse, Målgruppeanalyse",
            "Skadeservice, Kundeservice"})
    void compounds_ending_on_a_common_word_still_earn_credit_by_meaning(String ask, String held) {
        similar(ask, held, 0.8);

        assertThat(score(List.of(), held, ask)).isGreaterThan(score(List.of(), held, "Ukendt"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Field service", "Field services"})
    void a_generic_head_earns_nothing_by_meaning_in_either_number(String ask) {
        similar(ask, "Customer service", 0.8);

        assertThat(score(List.of(), "Customer service", ask))
                .isEqualTo(score(List.of(), "Customer service", "Ukendt"));
    }

    /** A posting requiring the held skill and {@code other}, scored for a profile holding one. */
    private int score(List<String> suggested, String held, String other) {
        return scorer(List.of(held), suggested).score(required(held, other), new ArrayList<>());
    }

    private void similar(String ask, String skill, double cosine) {
        similarities.put(ask + "|" + skill, cosine);
    }

    private SkillFitScorer scorer(List<String> held, List<String> suggested) {
        UUID userId = UUID.randomUUID();
        Profile profile = new Profile(UUID.randomUUID(), userId, null, null, null, List.of(),
                List.of(), null, null, null, null, null, null, null);
        List<ProfileSkill> skills = held.stream().map(name -> new ProfileSkill(UUID.randomUUID(),
                userId, name, null, null, null, false, 0, null)).toList();
        return SkillFitScorer.forProfile(canonicalizer, profile, skills, suggested, similarity);
    }

    private static Job required(String... asks) {
        return Job.builder().id(UUID.randomUUID()).title("Opslag")
                .requiredSkills(List.of(asks)).build();
    }
}
