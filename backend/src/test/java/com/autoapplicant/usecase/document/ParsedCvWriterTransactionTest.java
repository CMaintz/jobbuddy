package com.autoapplicant.usecase.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.autoapplicant.adapter.persistence.PostgresIntegrationTest;
import com.autoapplicant.adapter.persistence.adapter.ProfilePrivateInfoPersistenceAdapter;
import com.autoapplicant.adapter.persistence.adapter.ProfileSocialPersistenceAdapter;
import com.autoapplicant.usecase.skills.ImpliedSkillQueue;
import com.autoapplicant.usecase.skills.ProfileSkillIngestion;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A failure late in a CV import must not leave the earlier writes behind. The test runs outside
 * the usual per-test transaction: inside one, the writer would only join it and the rollback
 * under test could not be observed.
 */
@Import({ParsedCvWriter.class, ProfilePrivateInfoPersistenceAdapter.class,
        ProfileSocialPersistenceAdapter.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ParsedCvWriterTransactionTest extends PostgresIntegrationTest {

    private static final String CV_JSON = """
            {"fullName":"New Name","location":"Copenhagen",
             "linkedinUrl":"https://linkedin.com/in/new","skills":["Java"]}""";

    @Autowired ParsedCvWriter writer;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean ImpliedSkillQueue impliedSkills;
    @MockitoBean ProfileSkillIngestion skillIngestion;

    UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email) VALUES (?, ?)", userId, userId + "@example.com");
        jdbc.update("INSERT INTO profile_private_info (id, user_id, full_name, location) "
                + "VALUES (?, ?, 'Old Name', 'Aarhus')", UUID.randomUUID(), userId);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM profile_social WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM profile_private_info WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }

    @Test
    void a_failure_after_the_first_writes_leaves_the_stored_profile_unchanged() throws Exception {
        when(skillIngestion.ingest(eq(userId), any())).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> writer.write(userId, new ObjectMapper().readTree(CV_JSON)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject(
                "SELECT full_name FROM profile_private_info WHERE user_id = ?", String.class, userId))
                .isEqualTo("Old Name");
        assertThat(jdbc.queryForObject(
                "SELECT location FROM profile_private_info WHERE user_id = ?", String.class, userId))
                .isEqualTo("Aarhus");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM profile_social WHERE user_id = ?", Integer.class, userId))
                .isZero();
    }

    @Test
    void a_successful_write_commits_every_part() throws Exception {
        writer.write(userId, new ObjectMapper().readTree(CV_JSON));

        assertThat(jdbc.queryForObject(
                "SELECT full_name FROM profile_private_info WHERE user_id = ?", String.class, userId))
                .isEqualTo("New Name");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM profile_social WHERE user_id = ?", Integer.class, userId))
                .isEqualTo(1);
    }
}
