package com.autoapplicant.domain.job;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JobTextTest {

    private static final String POSTING = String.join("\n",
            "Platform Engineer",              // 1
            "We are hiring a platform engineer.", // 2
            "Requirements:",                  // 3
            "5 years of Kubernetes",          // 4
            "Danish at a professional level", // 5
            "Contact Marie on 12345678",      // 6
            "This site uses cookies.",        // 7
            "Accept all cookies");            // 8

    @Test
    void the_lines_the_model_marks_are_the_lines_that_go() {
        String stripped = JobText.stripLines(POSTING, List.of(new int[]{7, 8}));

        assertThat(stripped).doesNotContain("cookies");
        assertThat(stripped).contains("5 years of Kubernetes", "Contact Marie on 12345678");
        assertThat(stripped.split("\n")).hasSize(6);
    }

    @Test
    void a_range_the_model_got_backwards_still_cuts_the_right_lines() {
        assertThat(JobText.stripLines(POSTING, List.of(new int[]{8, 7})))
                .isEqualTo(JobText.stripLines(POSTING, List.of(new int[]{7, 8})));
    }

    @Test
    void a_range_running_off_the_end_is_clamped_rather_than_throwing() {
        assertThat(JobText.stripLines(POSTING, List.of(new int[]{7, 999})))
                .doesNotContain("cookies")
                .contains("Platform Engineer");
    }

    @Test
    void a_model_that_marks_almost_everything_is_ignored_and_the_posting_survives() {
        // Losing the posting is far worse than keeping a cookie banner, so a range
        // that would gut it is refused outright.
        assertThat(JobText.stripLines(POSTING, List.of(new int[]{1, 8}))).isEqualTo(POSTING);
        assertThat(JobText.stripLines(POSTING, List.of(new int[]{2, 8}))).isEqualTo(POSTING);
    }

    @Test
    void no_ranges_leaves_the_posting_exactly_as_it_was() {
        assertThat(JobText.stripLines(POSTING, List.of())).isEqualTo(POSTING);
        assertThat(JobText.stripLines(POSTING, null)).isEqualTo(POSTING);
    }

    @Test
    void the_numbering_the_model_sees_is_one_based_and_matches_what_strip_expects() {
        String numbered = JobText.numbered(POSTING);

        assertThat(numbered).startsWith("1: Platform Engineer\n");
        assertThat(numbered).contains("7: This site uses cookies.");
        // The line the model would cite as 7 is the line stripLines removes as 7.
        assertThat(JobText.stripLines(POSTING, List.of(new int[]{7, 7}))).doesNotContain("This site uses cookies.");
    }

    @Test
    void one_ceiling_governs_the_stored_text_and_the_text_the_model_is_shown() {
        String long_ = "x".repeat(JobText.MAX_DESCRIPTION_CHARS + 500);

        assertThat(JobText.truncate(long_)).hasSize(JobText.MAX_DESCRIPTION_CHARS);
        // Nothing is dropped a second time on the way into the prompt.
        assertThat(JobText.truncate(JobText.truncate(long_))).hasSize(JobText.MAX_DESCRIPTION_CHARS);
    }
}
