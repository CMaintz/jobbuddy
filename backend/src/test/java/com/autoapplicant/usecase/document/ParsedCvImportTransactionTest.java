package com.autoapplicant.usecase.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.autoapplicant.adapter.persistence.PostgresIntegrationTest;
import com.autoapplicant.adapter.persistence.adapter.ProfilePrivateInfoPersistenceAdapter;
import com.autoapplicant.adapter.persistence.adapter.ProfileSkillPersistenceAdapter;
import com.autoapplicant.adapter.persistence.adapter.ProfileSocialPersistenceAdapter;
import com.autoapplicant.adapter.persistence.adapter.SkillTaxonomyPersistenceAdapter;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.usecase.skills.ImpliedSkillQueue;
import com.autoapplicant.usecase.skills.ProfileSkillIngestion;
import com.autoapplicant.usecase.skills.SkillCanonicalizer;
import com.autoapplicant.usecase.skills.SkillResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction boundaries of a CV import, against a real database. The test runs outside the
 * usual per-test transaction: inside one, every write would only join it and neither the rollback
 * nor the per-row commits under test could be observed.
 */
@Import({ParsedCvContactWriter.class, ProfilePrivateInfoPersistenceAdapter.class,
        ProfileSocialPersistenceAdapter.class, ProfileSkillIngestion.class,
        ProfileSkillPersistenceAdapter.class, SkillResolver.class, SkillCanonicalizer.class,
        SkillTaxonomyPersistenceAdapter.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ParsedCvImportTransactionTest extends PostgresIntegrationTest {

    /** One past the profile_skills.skill_name column width, so the row fails in the database. */
    private static final String OVERLONG_SKILL = "x".repeat(201);

    @Autowired ParsedCvContactWriter contactWriter;
    @Autowired ProfileSkillIngestion skillIngestion;
    @Autowired JdbcTemplate jdbc;

    @MockitoSpyBean ProfileSocialPersistenceAdapter socials;
    @MockitoBean ImpliedSkillQueue impliedSkills;

    ChatProviderPort aiProvider = mock(ChatProviderPort.class);
    ParseCvService service;
    UUID userId;

    @BeforeEach
    void setUp() {
        service = new ParseCvService(aiProvider, new ObjectMapper(), contactWriter,
                impliedSkills, skillIngestion);
        userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email) VALUES (?, ?)", userId, userId + "@example.com");
        jdbc.update("INSERT INTO profile_private_info (id, user_id, full_name, location) "
                + "VALUES (?, ?, 'Old Name', 'Aarhus')", UUID.randomUUID(), userId);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM profile_skills WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM profile_social WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM profile_private_info WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }

    @Test
    void a_failure_partway_through_the_contact_details_leaves_the_stored_profile_unchanged() {
        aiReturns("""
                {"fullName":"New Name","location":"Copenhagen",
                 "linkedinUrl":"https://linkedin.com/in/new","skills":["Java"]}""");
        doThrow(new IllegalStateException("db down")).when(socials).save(any());

        assertThatThrownBy(() -> service.parseCvText(userId, "raw"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(privateInfo("full_name")).isEqualTo("Old Name");
        assertThat(privateInfo("location")).isEqualTo("Aarhus");
        assertThat(count("profile_social")).isZero();
        assertThat(count("profile_skills")).isZero();
    }

    @Test
    void one_bad_skill_row_is_skipped_without_failing_the_import() {
        aiReturns("{\"fullName\":\"New Name\",\"linkedinUrl\":\"https://linkedin.com/in/new\","
                + "\"skills\":[\"" + OVERLONG_SKILL + "\",\"Java\"]}");

        service.parseCvText(userId, "raw");

        assertThat(privateInfo("full_name")).isEqualTo("New Name");
        assertThat(count("profile_social")).isEqualTo(1);
        assertThat(jdbc.queryForList(
                "SELECT skill_name FROM profile_skills WHERE user_id = ?", String.class, userId))
                .containsExactly("Java");
    }

    private void aiReturns(String json) {
        when(aiProvider.generate(any(), any())).thenReturn(json);
    }

    private String privateInfo(String column) {
        return jdbc.queryForObject(
                "SELECT " + column + " FROM profile_private_info WHERE user_id = ?", String.class, userId);
    }

    private int count(String table) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE user_id = ?", Integer.class, userId);
    }
}
