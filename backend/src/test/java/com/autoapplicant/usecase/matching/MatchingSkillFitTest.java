package com.autoapplicant.usecase.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.matching.MatchResult;
import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.domain.skill.SkillTaxonomy;
import com.autoapplicant.domain.user.Profile;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Skill fit: requirement tiers, proficiency weighting and alias folding.
 *
 * <p>The point of the tier split: a must-have you lack must cost you, and a nice-to-have you hold
 * must be worth less than a must-have you hold. Before the split both were one flat list, so a
 * missing requirement scored exactly the same as a requirement that was never stated.
 */
class MatchingSkillFitTest extends MatchingServiceFixture {

    @Test
    void job_whose_requirements_are_all_met_outranks_one_with_a_missing_requirement() {
        UUID metId = UUID.randomUUID();
        UUID unmetId = UUID.randomUUID();
        Job met = tieredJob(metId, List.of("Java", "Spring"), List.of());
        Job unmet = tieredJob(unmetId, List.of("Java", "Kubernetes"), List.of());

        stubFeed(profileWithSkills("Java", "Spring"), List.of(met, unmet));
        List<MatchResult> results = service.getRecommendations(userId, 10);

        assertThat(scoreOf(results, metId)).isGreaterThan(scoreOf(results, unmetId));
    }

    @Test
    void a_missing_requirement_is_named_in_the_match_reasons() {
        UUID jobId = UUID.randomUUID();
        stubFeed(profileWithSkills("Java"),
                List.of(tieredJob(jobId, List.of("Java", "Kubernetes"), List.of())));

        List<MatchResult> results = service.getRecommendations(userId, 10);

        assertThat(results.get(0).matchReasons())
                .anyMatch(r -> r.contains("Missing required") && r.contains("Kubernetes"));
        assertThat(results.get(0).matchReasons()).contains("1/2 required skills covered");
    }

    @Test
    void holding_a_nice_to_have_raises_the_score_but_by_less_than_a_requirement() {
        UUID reqId = UUID.randomUUID();
        UUID niceId = UUID.randomUUID();
        UUID noneId = UUID.randomUUID();

        // Same single ask, differing only in the tier the posting put it in, plus a control
        // posting that asks for something the profile does not hold.
        stubFeed(profileWithSkills("Terraform"), List.of(
                tieredJob(reqId, List.of("Terraform"), List.of()),
                tieredJob(niceId, List.of(), List.of("Terraform")),
                tieredJob(noneId, List.of("Kubernetes"), List.of())));

        List<MatchResult> results = service.getRecommendations(userId, 10);

        assertThat(scoreOf(results, reqId)).isGreaterThan(scoreOf(results, niceId));
        assertThat(scoreOf(results, niceId)).isGreaterThan(scoreOf(results, noneId));
    }

    @Test
    void a_job_enriched_before_the_tiers_existed_still_scores_on_flat_overlap() {
        UUID overlapId = UUID.randomUUID();
        UUID unrelatedId = UUID.randomUUID();
        // Neither job has requiredSkills/preferredSkills: the pre-V069 shape.
        Job overlap = minimalJob(overlapId).toBuilder().technologies(List.of("Java")).build();
        Job unrelated = minimalJob(unrelatedId).toBuilder().technologies(List.of("COBOL")).build();

        stubFeed(profileWithSkills("Java"), List.of(overlap, unrelated));
        List<MatchResult> results = service.getRecommendations(userId, 10);

        assertThat(scoreOf(results, overlapId)).isGreaterThan(scoreOf(results, unrelatedId));
    }

    @Test
    void a_beginner_skill_never_used_in_production_covers_a_requirement_only_half() {
        // Required coverage 0.5 of 34 rounds to 17, plus the full 12 for the untiered overlap.
        assertThat(scoreWithJavaAt("BEGINNER", false)).isEqualTo(54);
    }

    @Test
    void a_beginner_skill_used_in_production_covers_a_requirement_fully() {
        assertThat(scoreWithJavaAt("BEGINNER", true)).isEqualTo(71);
    }

    @Test
    void taxonomy_aliases_fold_onto_one_skill_on_both_the_profile_and_the_posting_side() {
        when(taxonomyRepo.findAll()).thenReturn(List.of(
                new SkillTaxonomy(UUID.randomUUID(), "Kubernetes", "kubernetes", null, "devops",
                        List.of("k8s")),
                new SkillTaxonomy(UUID.randomUUID(), "PostgreSQL", "postgresql", null, "database",
                        List.of("postgres"))));
        UUID jobId = UUID.randomUUID();
        // The profile names one skill by its alias and the posting the other, so a missing
        // canonicalisation on either side leaves one requirement unmet.
        stubFeed(profileWithSkills("k8s", "PostgreSQL"),
                List.of(tieredJob(jobId, List.of("Kubernetes", "postgres"), List.of())));

        List<MatchResult> results = service.getRecommendations(userId, 10);

        assertThat(scoreOf(results, jobId)).isEqualTo(71);
        assertThat(reasonsFor(results, jobId)).contains("2/2 required skills covered");
    }

    private int scoreWithJavaAt(String proficiency, boolean usedInProduction) {
        Profile p = profile("Dev", "Java", List.of());
        when(profileSkillRepo.findByUserId(userId)).thenReturn(List.of(
                new ProfileSkill(UUID.randomUUID(), userId, "Java", null,
                        proficiency, null, usedInProduction, 0, null)));
        stubFeed(p, List.of(tieredJob(UUID.randomUUID(), List.of("Java"), List.of())));
        return service.getRecommendations(userId, 10).get(0).totalScore();
    }
}
