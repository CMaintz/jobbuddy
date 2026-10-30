package com.autoapplicant.usecase.matching;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.domain.job.EmploymentType;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.JobCategory;
import com.autoapplicant.domain.job.RemoteType;
import com.autoapplicant.domain.job.Seniority;
import com.autoapplicant.domain.matching.MatchResult;
import com.autoapplicant.domain.user.UserPreferences;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Preferences the user set act as hard filters: a posting that contradicts one is never shown.
 * A posting that leaves the field blank is given the benefit of the doubt.
 */
class MatchingHardFiltersTest extends MatchingServiceFixture {

    private final UUID kept = UUID.randomUUID();
    private final UUID dropped = UUID.randomUUID();

    @Test
    void an_excluded_company_is_matched_case_insensitively_on_a_substring() {
        Job excluded = minimalJob(dropped).toBuilder().companyName("ACME Corp A/S").build();
        Job other = minimalJob(kept).toBuilder().companyName("Globex").build();

        assertThat(feedFor(prefs().excludedCompanies(" acme").build(), excluded, other))
                .containsExactly(kept);
    }

    @Test
    void a_job_outside_the_preferred_remote_types_is_dropped() {
        Job remote = minimalJob(kept).toBuilder().remoteType(RemoteType.REMOTE).build();
        Job onSite = minimalJob(dropped).toBuilder().remoteType(RemoteType.ON_SITE).build();

        assertThat(feedFor(prefs().remoteTypes("REMOTE", "HYBRID").build(), remote, onSite))
                .containsExactly(kept);
    }

    @Test
    void a_job_with_no_stated_remote_type_is_not_dropped_on_remote_grounds() {
        assertThat(feedFor(prefs().remoteTypes("REMOTE").build(), minimalJob(kept)))
                .containsExactly(kept);
    }

    @Test
    void a_job_outside_the_preferred_employment_types_is_dropped() {
        Job fullTime = minimalJob(kept).toBuilder().employmentType(EmploymentType.FULL_TIME).build();
        Job contract = minimalJob(dropped).toBuilder().employmentType(EmploymentType.CONTRACT).build();

        assertThat(feedFor(prefs().employmentTypes("FULL_TIME").build(), fullTime, contract))
                .containsExactly(kept);
    }

    @Test
    void a_job_outside_the_preferred_seniority_is_dropped() {
        Job senior = minimalJob(kept).toBuilder().seniority(Seniority.SENIOR).build();
        Job junior = minimalJob(dropped).toBuilder().seniority(Seniority.JUNIOR).build();

        assertThat(feedFor(prefs().seniority("SENIOR").build(), senior, junior))
                .containsExactly(kept);
    }

    @Test
    void a_job_outside_the_preferred_industries_is_dropped() {
        Job software = minimalJob(kept).toBuilder().jobCategory(JobCategory.SOFTWARE_IT).build();
        Job finance = minimalJob(dropped).toBuilder().jobCategory(JobCategory.FINANCE).build();

        assertThat(feedFor(prefs().industries("SOFTWARE_IT").build(), software, finance))
                .containsExactly(kept);
    }

    @Test
    void an_unclassified_job_is_never_dropped_on_industry_grounds() {
        UUID uncategorised = UUID.randomUUID();
        Job other = minimalJob(kept).toBuilder().jobCategory(JobCategory.OTHER).build();

        assertThat(feedFor(prefs().industries("SOFTWARE_IT").build(),
                other, minimalJob(uncategorised)))
                .containsExactlyInAnyOrder(kept, uncategorised);
    }

    @Test
    void a_job_beyond_the_commute_radius_is_dropped() {
        // Frederiksberg is about 2 km from central Copenhagen, Aarhus about 155 km.
        Job near = minimalJob(kept).toBuilder().municipality("Frederiksberg").build();
        Job far = minimalJob(dropped).toBuilder().municipality("Aarhus").build();

        assertThat(feedFor(radius30FromCopenhagen(), near, far)).containsExactly(kept);
    }

    @Test
    void a_fully_remote_job_is_exempt_from_the_commute_radius() {
        Job remoteFar = minimalJob(kept).toBuilder()
                .municipality("Aarhus").remoteType(RemoteType.REMOTE).build();
        Job hybridFar = minimalJob(dropped).toBuilder()
                .municipality("Aarhus").remoteType(RemoteType.HYBRID).build();

        assertThat(feedFor(radius30FromCopenhagen(), remoteFar, hybridFar)).containsExactly(kept);
    }

    @Test
    void a_job_in_a_municipality_with_no_known_coordinates_passes_the_commute_radius() {
        Job unplaceable = minimalJob(kept).toBuilder().municipality("Atlantis").build();

        assertThat(feedFor(radius30FromCopenhagen(), unplaceable)).containsExactly(kept);
    }

    @Test
    void a_home_point_with_no_known_coordinates_filters_nothing() {
        // A misspelt or unlisted home municipality gives no distance to judge by. Treating that
        // as "too far" would empty the feed of every posting that can be placed.
        Job near = minimalJob(kept).toBuilder().municipality("Frederiksberg").build();
        UserPreferences prefs = prefs().municipalities("Atlantis").maxCommuteKm(30).build();

        assertThat(feedFor(prefs, near)).containsExactly(kept);
    }

    @Test
    void an_unplaceable_home_point_does_not_disable_the_radius_of_a_placeable_one() {
        Job near = minimalJob(kept).toBuilder().municipality("Frederiksberg").build();
        Job far = minimalJob(dropped).toBuilder().municipality("Aarhus").build();
        UserPreferences prefs =
                prefs().municipalities("Copenhagen", "Atlantis").maxCommuteKm(30).build();

        assertThat(feedFor(prefs, near, far)).containsExactly(kept);
    }

    @Test
    void the_commute_radius_falls_back_to_preferred_locations_as_the_home_point() {
        Job near = minimalJob(kept).toBuilder().municipality("Frederiksberg").build();
        Job far = minimalJob(dropped).toBuilder().municipality("Aarhus").build();
        UserPreferences prefs = prefs().locations("Copenhagen").maxCommuteKm(30).build();

        assertThat(feedFor(prefs, near, far)).containsExactly(kept);
    }

    private UserPreferences radius30FromCopenhagen() {
        return prefs().municipalities("Copenhagen").maxCommuteKm(30).build();
    }

    private List<UUID> feedFor(UserPreferences prefs, Job... jobs) {
        stubPrefs(prefs);
        stubFeed(profileWithSkills("Java"), List.of(jobs));
        return service.getRecommendations(userId, 10).stream().map(MatchResult::jobId).toList();
    }
}
