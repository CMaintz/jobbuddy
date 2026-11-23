package com.autoapplicant.adapter.persistence.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.adapter.persistence.JobRows;
import com.autoapplicant.adapter.persistence.PostgresIntegrationTest;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.search.JobSearchQuery;
import com.autoapplicant.domain.search.JobSearchResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(PostgresJobSearchAdapter.class)
class SearchSkipListTest extends PostgresIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");

    @Autowired PostgresJobSearchAdapter search;
    @Autowired JdbcTemplate jdbc;

    @Test
    void aSkippedCategoryIsLeftOutOfSearchResultsAndTheirCount() {
        // Bug: keyword search ignores her skip list, so a category she never wants to see comes
        // back the moment she searches; or the count still includes it and the pager lies.
        JobRows jobs = new JobRows(jdbc);
        UUID software = jobs.insert("Java udvikler", "Spring Boot.", NOW, true, "SOFTWARE_IT");
        UUID uncategorised = jobs.insert("Java udvikler", "Uden kategori.", NOW);
        jobs.insert("Java salgskonsulent", "Salg af Java-licenser.", NOW, true, "SALES");

        JobSearchResult result = search.search(new JobSearchQuery("java", null, 0, 20, "postedAt"),
                List.of("SALES"));

        assertThat(result.jobs()).extracting(Job::id).containsExactlyInAnyOrder(software, uncategorised);
        assertThat(result.total()).isEqualTo(2);
    }
}
