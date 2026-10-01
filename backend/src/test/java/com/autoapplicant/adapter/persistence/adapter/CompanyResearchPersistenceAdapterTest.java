package com.autoapplicant.adapter.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.adapter.persistence.PostgresIntegrationTest;
import com.autoapplicant.domain.company.CompanyResearch;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(CompanyResearchPersistenceAdapter.class)
class CompanyResearchPersistenceAdapterTest extends PostgresIntegrationTest {

    @Autowired CompanyResearchPersistenceAdapter research;
    @Autowired JdbcTemplate jdbc;

    UUID alice;
    UUID bob;
    UUID company;

    @BeforeEach
    void setUp() {
        alice = insertUser();
        bob = insertUser();
        company = UUID.randomUUID();
        jdbc.update("INSERT INTO companies (id, name) VALUES (?, 'Acme ApS')", company);
    }

    @Test
    void two_users_keep_their_own_notes_on_the_same_company() {
        research.saveResearch(alice, company, "Alice's notes");
        research.saveResearch(bob, company, "Bob's notes");

        assertThat(research.findResearch(alice, company)).map(CompanyResearch::notes).contains("Alice's notes");
        assertThat(research.findResearch(bob, company)).map(CompanyResearch::notes).contains("Bob's notes");
        assertThat(rowCount()).isEqualTo(2);
    }

    @Test
    void saving_again_updates_in_place_and_stamps_the_time() {
        research.saveResearch(alice, company, "first draft");
        research.saveResearch(alice, company, "second draft");

        assertThat(research.findResearch(alice, company)).hasValueSatisfying(r -> {
            assertThat(r.notes()).isEqualTo("second draft");
            assertThat(r.updatedAt()).isNotNull();
        });
        assertThat(rowCount()).isEqualTo(1);
    }

    @Test
    void blank_notes_delete_only_that_users_row() {
        research.saveResearch(alice, company, "Alice's notes");
        research.saveResearch(bob, company, "Bob's notes");

        research.saveResearch(bob, company, " ");

        assertThat(research.findResearch(bob, company)).isEmpty();
        assertThat(research.findResearch(alice, company)).isPresent();
    }

    @Test
    void clearing_notes_that_were_never_saved_is_a_no_op() {
        research.saveResearch(alice, company, null);

        assertThat(research.findResearch(alice, company)).isEmpty();
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email) VALUES (?, ?)", id, id + "@example.com");
        return id;
    }

    private int rowCount() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM user_company_notes WHERE company_id = ?", Integer.class, company);
    }
}
