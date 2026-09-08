package com.autoapplicant.usecase.document;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentFactGuardTest {

    private final DocumentFactGuard guard = new DocumentFactGuard();

    private static final String SOURCE = String.join(" ",
            "Reached 16,181 active users and 289,760 enrollments across 80 courses.",
            "Cut infrastructure cost 60%. Managed a $550K budget.",
            "Certified partners earned 2x more. Authored 80+ open-access technical guides.");

    @Test
    void truthfulMetricPasses() {
        assertThat(guard.audit("Reached 16,181 users", SOURCE).clean()).isTrue();
    }

    @Test
    void inflatedCountIsCaught() {
        assertThat(guard.audit("Reached 94,772 active users", SOURCE).inventedMetrics())
                .containsExactly("94772 users");
    }

    @Test
    void newMetricNounIsCaught() {
        assertThat(guard.audit("Drove 900,000 enrollments", SOURCE).inventedMetrics())
                .containsExactly("900000 enrollments");
    }

    @Test
    void truthfulCurrencyPassesAndInflatedIsCaught() {
        assertThat(guard.audit("Managed a $550K budget", SOURCE).clean()).isTrue();
        assertThat(guard.audit("Managed a $900K budget", SOURCE).inventedMetrics())
                .containsExactly("$900k");
    }

    @Test
    void truthfulPercentAndMultiplierPass() {
        assertThat(guard.audit("Cut cost 60%", SOURCE).clean()).isTrue();
        assertThat(guard.audit("Partners earned 2x more", SOURCE).clean()).isTrue();
    }

    @Test
    void nounSynonymPasses() {
        assertThat(guard.audit("Authored 80 articles", SOURCE).clean()).isTrue();
    }

    @Test
    void ordinaryYearIsIgnored() {
        assertThat(guard.audit("Joined the team in 2013", SOURCE).clean()).isTrue();
    }

    @Test
    void inflatedMagnitudeSuffixIsCaught() {
        assertThat(guard.audit("Grew the product to 50k users", "Reached 50 users.").inventedMetrics())
                .containsExactly("50k users");
    }

    @Test
    void magnitudeSuffixSupportedBySourcePasses() {
        assertThat(guard.audit("Grew to 50k users", "Reached 50k users.").clean()).isTrue();
    }

    @Test
    void thousandsGroupingStyleDoesNotDecideMatch() {
        // period-grouped document vs comma-grouped source describe the same number
        assertThat(guard.audit("Reached 16.181 users", SOURCE).clean()).isTrue();
        assertThat(guard.audit("Reached 16 181 users", SOURCE).clean()).isTrue();
    }
}
