package com.autoapplicant.domain.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The frontend reads {@code job.isActive} to grey out closed postings and to hide the
 * apply action. Jackson names a record component after the component, not after a
 * bean-style getter, so the key is {@code isActive} and not {@code active} — pin it,
 * because getting it wrong fails silently: the badge simply never appears.
 */
class JobJsonShapeTest {

    @Test
    void the_active_flag_reaches_the_client_under_the_name_the_client_reads() throws Exception {
        Job job = Job.builder().title("Platform Engineer").isActive(false).build();
        String json = new ObjectMapper().writeValueAsString(job);
        assertThat(json).contains("\"isActive\":false");
        assertThat(json).doesNotContain("\"active\":");
    }
}
