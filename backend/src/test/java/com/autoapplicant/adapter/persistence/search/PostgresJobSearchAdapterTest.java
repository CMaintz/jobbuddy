package com.autoapplicant.adapter.persistence.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.adapter.persistence.JobRows;
import com.autoapplicant.adapter.persistence.PostgresIntegrationTest;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.search.JobSearchFilters;
import com.autoapplicant.domain.search.JobSearchQuery;
import com.autoapplicant.domain.search.JobSearchResult;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(PostgresJobSearchAdapter.class)
class PostgresJobSearchAdapterTest extends PostgresIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");

    @Autowired PostgresJobSearchAdapter search;
    @Autowired JdbcTemplate jdbc;

    JobRows jobs;

    @BeforeEach
    void setUp() {
        jobs = new JobRows(jdbc);
    }

    @Test
    void a_title_hit_outranks_a_newer_posting_that_only_mentions_the_term_in_its_body() {
        UUID bodyOnly = jobs.insert("Projektleder", "Du samarbejder med vores frontend team.", NOW);
        UUID titleHit = jobs.insert("Frontend udvikler", "Vi bygger webapps.", NOW.minus(10, ChronoUnit.DAYS));

        List<UUID> ids = ids(search.search(query("frontend", null)));

        assertThat(ids).containsExactly(titleHit, bodyOnly);
    }

    @Test
    void danish_stemming_matches_inflected_forms() {
        UUID job = jobs.insert("Erfaren udvikler", "Backend med Java.", NOW);

        List<UUID> ids = ids(search.search(query("udviklere", null)));

        assertThat(ids).containsExactly(job);
    }

    @Test
    void web_search_syntax_excludes_negated_terms() {
        UUID kept = jobs.insert("Java udvikler", "Spring Boot og Postgres.", NOW);
        jobs.insert("Java udvikler", "Primært Kotlin.", NOW);

        List<UUID> ids = ids(search.search(query("java -kotlin", null)));

        assertThat(ids).containsExactly(kept);
    }

    @Test
    void inactive_postings_never_appear() {
        UUID active = jobs.insert("Data engineer", "Pipelines.", NOW);
        jobs.insert("Data engineer", "Pipelines.", NOW, false, null);

        JobSearchResult result = search.search(query("engineer", null));

        assertThat(ids(result)).containsExactly(active);
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void an_empty_query_browses_newest_first_and_counts_all_active_rows() {
        UUID older = jobs.insert("Tester", "QA.", NOW.minus(2, ChronoUnit.DAYS));
        UUID newest = jobs.insert("Designer", "UX.", NOW);
        UUID middle = jobs.insert("Analytiker", "BI.", NOW.minus(1, ChronoUnit.DAYS));

        JobSearchResult result = search.search(query("  ", null));

        assertThat(ids(result)).containsExactly(newest, middle, older);
        assertThat(result.total()).isEqualTo(3);
    }

    @Test
    void pages_are_sliced_while_the_total_stays_the_full_count() {
        for (int i = 0; i < 5; i++) {
            jobs.insert("Konsulent " + i, "Rådgivning.", NOW.minus(i, ChronoUnit.HOURS));
        }

        JobSearchResult secondPage = search.search(new JobSearchQuery("konsulent", null, 1, 2, null, null));

        assertThat(secondPage.jobs()).hasSize(2);
        assertThat(secondPage.total()).isEqualTo(5);
    }

    @Test
    void the_category_filter_narrows_both_rows_and_total() {
        UUID it = jobs.insert("Udvikler", "Kode.", NOW, true, "IT");
        jobs.insert("Udvikler", "Kode.", NOW, true, "SALES");

        JobSearchResult result = search.search(query("udvikler", List.of("IT")));

        assertThat(ids(result)).containsExactly(it);
        assertThat(result.total()).isEqualTo(1);
    }

    private static JobSearchQuery query(String text, List<String> categories) {
        JobSearchFilters filters = categories == null
                ? null
                : new JobSearchFilters(null, null, null, null, null, null, null, null, null, null, categories);
        return new JobSearchQuery(text, filters, 0, 20, null, null);
    }

    private static List<UUID> ids(JobSearchResult result) {
        return result.jobs().stream().map(Job::id).toList();
    }
}
