package com.autoapplicant.adapter.web.dto.job;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.JobText;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A list of roles must not ship a full posting per row. The reader gets the opening
 * and fetches the rest for the one they open.
 */
class JobResponsePreviewTest {

    private static Job jobWithDescription(String description) {
        return Job.builder().title("Platform Engineer").descriptionClean(description).build();
    }

    @Test
    void a_list_row_carries_only_the_opening_and_says_so() {
        Job job = jobWithDescription("x".repeat(JobText.MAX_DESCRIPTION_CHARS));

        JobResponse response = JobResponse.preview(job);

        assertThat(response.descriptionClean()).hasSizeLessThanOrEqualTo(JobText.PREVIEW_CHARS);
        assertThat(response.descriptionTruncated()).isTrue();
    }

    @Test
    void a_posting_short_enough_to_fit_is_not_flagged_as_cut() {
        Job job = jobWithDescription("A short posting.");

        JobResponse response = JobResponse.preview(job);

        assertThat(response.descriptionClean()).isEqualTo("A short posting.");
        assertThat(response.descriptionTruncated()).isFalse();
    }

    @Test
    void the_job_asked_for_by_id_still_carries_the_whole_posting() {
        String full = "y".repeat(JobText.MAX_DESCRIPTION_CHARS);

        JobResponse response = JobResponse.from(jobWithDescription(full));

        assertThat(response.descriptionClean()).isEqualTo(full);
        assertThat(response.descriptionTruncated()).isFalse();
    }

    @Test
    void the_preview_breaks_at_a_line_rather_than_mid_sentence() {
        String head = "Platform Engineer\n\n" + "Om rollen\n".repeat(80);
        Job job = jobWithDescription(head + "x".repeat(2000));

        String preview = JobResponse.preview(job).descriptionClean();

        assertThat(preview).doesNotEndWith("x");
        assertThat(preview.lines().toList().getLast()).isEqualTo("Om rollen");
    }

    @Test
    void a_posting_with_no_description_survives_both_paths() {
        Job job = jobWithDescription(null);

        assertThat(JobResponse.preview(job).descriptionClean()).isNull();
        assertThat(JobResponse.preview(job).descriptionTruncated()).isFalse();
        assertThat(JobResponse.from(job).descriptionClean()).isNull();
    }
}
