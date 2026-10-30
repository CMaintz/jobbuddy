package com.autoapplicant.adapter.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.adapter.persistence.JobRows;
import com.autoapplicant.adapter.persistence.PostgresIntegrationTest;
import com.autoapplicant.domain.application.Application;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Every application read in the service goes through findByIdAndUserId, so a user who guesses
 * another user's application id must get exactly what a missing id gives them: nothing.
 */
@Import(ApplicationPersistenceAdapter.class)
class ApplicationPersistenceAdapterOwnershipTest extends PostgresIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");

    @Autowired ApplicationPersistenceAdapter adapter;
    @Autowired JdbcTemplate jdbc;

    UUID owner;
    UUID stranger;
    UUID jobId;
    UUID applicationId;

    @BeforeEach
    void setUp() {
        owner = insertUser();
        stranger = insertUser();
        jobId = new JobRows(jdbc).insert("Developer", "d", NOW);
        applicationId = UUID.randomUUID();
        jdbc.update("INSERT INTO applications (id, user_id, job_id, status) VALUES (?, ?, ?, 'SAVED')",
                applicationId, owner, jobId);
    }

    @Test
    void the_owner_finds_their_application() {
        assertThat(adapter.findByIdAndUserId(applicationId, owner))
                .map(Application::userId)
                .contains(owner);
    }

    @Test
    void another_user_cannot_read_it_by_id() {
        assertThat(adapter.findByIdAndUserId(applicationId, stranger)).isEmpty();
    }

    @Test
    void another_users_application_does_not_block_applying_to_the_same_job() {
        assertThat(adapter.existsByUserIdAndJobId(owner, jobId)).isTrue();
        assertThat(adapter.existsByUserIdAndJobId(stranger, jobId)).isFalse();
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email) VALUES (?, ?)", id, id + "@example.com");
        return id;
    }
}
