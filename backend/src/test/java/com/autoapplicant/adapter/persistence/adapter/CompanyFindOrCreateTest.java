package com.autoapplicant.adapter.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.adapter.persistence.PostgresIntegrationTest;
import com.autoapplicant.domain.company.Company;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** Each source names the employer its own way; the companies table should hold it once. */
@Import(CompanyPersistenceAdapter.class)
class CompanyFindOrCreateTest extends PostgresIntegrationTest {

    @Autowired CompanyPersistenceAdapter companies;
    @Autowired JdbcTemplate jdbc;

    private UUID existing(String name) {
        return existing(name, Instant.now());
    }

    private UUID existing(String name, Instant createdAt) {
        UUID id = UUID.randomUUID();
        Timestamp at = Timestamp.from(createdAt);
        jdbc.update("INSERT INTO companies (id, name, created_at, updated_at) VALUES (?, ?, ?, ?)",
                id, name, at, at);
        return id;
    }

    @Test
    void aJobindexNameResolvesToTheCompanyALeverSlugCreated() {
        // Bug: lookup by exact name only, so "veo" from Lever and "Veo Technologies ApS" from
        // Jobindex become two companies with split notes and research.
        UUID veo = existing("veo");

        assertThat(companies.findOrCreate("Veo Technologies ApS").id()).isEqualTo(veo);
    }

    @Test
    void aCompanySharingOnlyAPrefixGetsItsOwnRow() {
        // Bug: candidates are found by the first word, and taking the first candidate rather than
        // the one whose key matches files Novo Nordisk under the foundation that owns it.
        UUID foundation = existing("Novo Nordisk Foundation");

        Company created = companies.findOrCreate("Novo Nordisk A/S");

        assertThat(created.id()).isNotEqualTo(foundation);
        assertThat(created.name()).isEqualTo("Novo Nordisk A/S");
    }

    @Test
    void anExactNameWinsOverAnotherRowWithTheSameKey() {
        // Bug: when old duplicates already share a key, picking by key alone can move a
        // company's postings onto its twin on every crawl.
        existing("Veo Technologies");
        UUID exact = existing("Veo");

        assertThat(companies.findOrCreate("Veo").id()).isEqualTo(exact);
    }

    @Test
    void whenOldDuplicatesShareAKeyTheOldestOneIsChosen() {
        // Bug: an unordered candidate query picks whichever twin the database returns first, so
        // postings from the same employer alternate between the two rows across crawls.
        Instant now = Instant.now();
        existing("Veo Technologies A/S", now);
        UUID oldest = existing("veo", now.minusSeconds(3600));

        assertThat(companies.findOrCreate("Veo Technologies ApS").id()).isEqualTo(oldest);
    }
}
