package com.autoapplicant.usecase.matching;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.RemoteType;
import com.autoapplicant.domain.matching.FeedbackType;
import com.autoapplicant.domain.matching.MatchLabel;
import com.autoapplicant.domain.matching.MatchResult;
import com.autoapplicant.domain.user.UserPreferences;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Soft preference scoring on top of the 25-point base: salary, location proximity and keyword
 * signals, and the 0..100 clamp on the total.
 */
class MatchingPreferenceScoringTest extends MatchingServiceFixture {

    @Test
    void salary_inside_the_range_adds_six_and_outside_it_subtracts_six() {
        UUID inRange = UUID.randomUUID();
        UUID below = UUID.randomUUID();
        UUID above = UUID.randomUUID();
        UUID unstated = UUID.randomUUID();
        // The job's midpoint is what is compared: 50k, 32k and 75k against a 40k-60k range.
        List<MatchResult> results = feedFor(prefs().salary(40_000, 60_000).build(),
                salaried(inRange, 45_000, 55_000),
                salaried(below, 30_000, 34_000),
                salaried(above, 70_000, 80_000),
                minimalJob(unstated));

        assertThat(scoreOf(results, inRange)).isEqualTo(31);
        assertThat(scoreOf(results, below)).isEqualTo(19);
        assertThat(scoreOf(results, above)).isEqualTo(19);
        assertThat(scoreOf(results, unstated)).isEqualTo(25);
    }

    @Test
    void location_bonus_tapers_with_distance_from_the_preferred_municipality() {
        UUID home = UUID.randomUUID();
        UUID roskilde = UUID.randomUUID();
        UUID aarhus = UUID.randomUUID();
        // No radius, so the taper runs over a nominal 50 km: Roskilde at ~31 km keeps 3 of 8.
        List<MatchResult> results = feedFor(prefs().municipalities("Copenhagen").build(),
                inMunicipality(home, "Copenhagen"),
                inMunicipality(roskilde, "Roskilde"),
                inMunicipality(aarhus, "Aarhus"));

        assertThat(scoreOf(results, home)).isEqualTo(33);
        assertThat(scoreOf(results, roskilde)).isEqualTo(28);
        assertThat(scoreOf(results, aarhus)).isEqualTo(25);
    }

    @Test
    void the_commute_radius_is_the_band_the_location_bonus_tapers_over() {
        UUID roskilde = UUID.randomUUID();
        // ~31 km into a 40 km band keeps 2 of 8, where the nominal 50 km band would keep 3.
        List<MatchResult> results = feedFor(
                prefs().municipalities("Copenhagen").maxCommuteKm(40).build(),
                inMunicipality(roskilde, "Roskilde"));

        assertThat(scoreOf(results, roskilde)).isEqualTo(27);
    }

    @Test
    void a_fully_remote_job_earns_the_full_location_bonus_wherever_it_is() {
        UUID remote = UUID.randomUUID();
        Job job = inMunicipality(remote, "Aarhus").toBuilder().remoteType(RemoteType.REMOTE).build();

        List<MatchResult> results = feedFor(prefs().municipalities("Copenhagen").build(), job);

        assertThat(scoreOf(results, remote)).isEqualTo(33);
    }

    @Test
    void without_preferred_municipalities_a_preferred_location_earns_the_location_bonus() {
        UUID there = UUID.randomUUID();
        UUID elsewhere = UUID.randomUUID();
        List<MatchResult> results = feedFor(prefs().locations("copenhagen").build(),
                minimalJob(there),
                minimalJob(elsewhere).toBuilder().location("Aarhus").build());

        assertThat(scoreOf(results, there)).isEqualTo(33);
        assertThat(scoreOf(results, elsewhere)).isEqualTo(25);
    }

    @Test
    void each_positive_signal_adds_four_up_to_twelve() {
        UUID one = UUID.randomUUID();
        UUID four = UUID.randomUUID();
        List<MatchResult> results = feedFor(
                prefs().positiveSignals("kotlin", "scala", "groovy", "clojure").build(),
                described(one, "We write Kotlin."),
                described(four, "Kotlin, Scala, Groovy and Clojure."));

        assertThat(scoreOf(results, one)).isEqualTo(29);
        assertThat(scoreOf(results, four)).isEqualTo(37);
    }

    @Test
    void each_negative_signal_subtracts_six_down_to_eighteen() {
        UUID one = UUID.randomUUID();
        UUID four = UUID.randomUUID();
        List<MatchResult> results = feedFor(
                prefs().negativeSignals("cobol", "mainframe", "fax", "overtime").build(),
                described(one, "Some COBOL."),
                described(four, "COBOL on a mainframe, by fax, with overtime."));

        assertThat(scoreOf(results, one)).isEqualTo(19);
        assertThat(scoreOf(results, four)).isEqualTo(7);
    }

    @Test
    void total_score_is_capped_at_one_hundred() {
        UUID jobId = UUID.randomUUID();
        // 25 base + 55 full skill overlap + 12 capped signals + 10 like = 102 before the cap.
        Job job = described(jobId, "Kotlin, Scala and Groovy.").toBuilder()
                .technologies(List.of("Java")).build();
        stubFeedback(feedback(jobId, FeedbackType.LIKE));

        MatchResult result = feedFor(
                prefs().positiveSignals("kotlin", "scala", "groovy").build(), job).get(0);

        assertThat(result.totalScore()).isEqualTo(100);
        assertThat(result.matchLabel()).isEqualTo(MatchLabel.EXCELLENT);
    }

    @Test
    void total_score_never_drops_below_zero() {
        UUID jobId = UUID.randomUUID();
        // 25 base - 6 salary - 18 capped signals - 20 dislike = -19 before the floor.
        Job job = described(jobId, "COBOL on a mainframe, by fax.").toBuilder()
                .salaryMin(10_000).salaryMax(10_000).build();
        stubFeedback(feedback(jobId, FeedbackType.DISLIKE));

        MatchResult result = feedFor(prefs().salary(40_000, 60_000)
                .negativeSignals("cobol", "mainframe", "fax").build(), job).get(0);

        assertThat(result.totalScore()).isZero();
    }

    private List<MatchResult> feedFor(UserPreferences prefs, Job... jobs) {
        stubPrefs(prefs);
        stubFeed(profileWithSkills("Java"), List.of(jobs));
        return service.getRecommendations(userId, 10);
    }

    private static Job salaried(UUID id, int min, int max) {
        return minimalJob(id).toBuilder().salaryMin(min).salaryMax(max).build();
    }

    private static Job inMunicipality(UUID id, String municipality) {
        return minimalJob(id).toBuilder().municipality(municipality).build();
    }

    private static Job described(UUID id, String description) {
        return minimalJob(id).toBuilder().descriptionClean(description).build();
    }
}
