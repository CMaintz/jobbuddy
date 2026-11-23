package com.autoapplicant.adapter.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.autoapplicant.adapter.persistence.JobRows;
import com.autoapplicant.adapter.persistence.PostgresIntegrationTest;
import com.autoapplicant.domain.application.Application;
import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.domain.user.UiLanguage;
import com.autoapplicant.domain.user.User;
import com.autoapplicant.domain.user.UserRole;
import com.autoapplicant.port.out.application.ApplicationStatusEventRepositoryPort;
import com.autoapplicant.port.out.document.GeneratedDocumentRepositoryPort;
import com.autoapplicant.usecase.application.ApplicationService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The user's own deadline and interview time, and their UI language, through V041 on a real
 * Postgres: a field dropped by the mapper or a column the migration forgot only shows up here.
 */
@Import({ApplicationPersistenceAdapter.class, UserPersistenceAdapter.class})
class ApplicationDatesPersistenceTest extends PostgresIntegrationTest {

    private static final LocalDate DEADLINE = LocalDate.of(2026, 11, 18);
    private static final LocalDateTime INTERVIEW = LocalDateTime.of(2026, 11, 20, 9, 0);

    @Autowired ApplicationPersistenceAdapter applications;
    @Autowired UserPersistenceAdapter users;
    @Autowired JdbcTemplate jdbc;

    private ApplicationService service;
    private UUID userId;
    private UUID jobId;

    @BeforeEach
    void setUp() {
        service = new ApplicationService(applications,
                Mockito.mock(ApplicationStatusEventRepositoryPort.class),
                Mockito.mock(GeneratedDocumentRepositoryPort.class));
        userId = users.save(new User(null, UUID.randomUUID() + "@example.dk", null, null, null,
                UserRole.USER, true, true, null, null)).id();
        jobId = new JobRows(jdbc).insert("Kommunikationsmedarbejder", "", Instant.now());
    }

    private Application savedWithDates() {
        Application created = service.createApplication(userId, jobId, null, null);
        return service.updateDates(created.id(), userId, DEADLINE, INTERVIEW);
    }

    @Test
    void theDatesAreStoredAsTyped() {
        Application saved = savedWithDates();

        Application reloaded = applications.findByIdAndUserId(saved.id(), userId).orElseThrow();
        assertThat(reloaded.deadline()).isEqualTo(DEADLINE);
        assertThat(reloaded.interviewAt()).isEqualTo(INTERVIEW);
    }

    @Test
    void aStatusChangeKeepsTheDates() {
        Application saved = savedWithDates();

        service.updateStatus(saved.id(), userId, ApplicationStatus.PREPARING, null);

        Application reloaded = applications.findByIdAndUserId(saved.id(), userId).orElseThrow();
        assertThat(reloaded.deadline()).isEqualTo(DEADLINE);
        assertThat(reloaded.interviewAt()).isEqualTo(INTERVIEW);
    }

    @Test
    void emptyDatesClearWhatWasThere() {
        Application saved = savedWithDates();

        service.updateDates(saved.id(), userId, null, null);

        Application reloaded = applications.findByIdAndUserId(saved.id(), userId).orElseThrow();
        assertThat(reloaded.deadline()).isNull();
        assertThat(reloaded.interviewAt()).isNull();
    }

    @Test
    void anotherUserCannotChangeTheDates() {
        Application saved = savedWithDates();
        UUID otherUser = users.save(new User(null, UUID.randomUUID() + "@example.dk", null, null, null,
                UserRole.USER, true, true, null, null)).id();

        assertThatThrownBy(() -> service.updateDates(saved.id(), otherUser, null, null))
                .isInstanceOf(IllegalArgumentException.class);

        Application reloaded = applications.findByIdAndUserId(saved.id(), userId).orElseThrow();
        assertThat(reloaded.deadline()).isEqualTo(DEADLINE);
        assertThat(reloaded.interviewAt()).isEqualTo(INTERVIEW);
    }

    @Test
    void aNewUserReadsDanishAndKeepsTheLanguageTheyPickAcrossAccountSaves() {
        assertThat(users.findLanguage(userId)).contains(UiLanguage.DA);

        users.saveLanguage(userId, UiLanguage.EN);
        users.save(users.findById(userId).orElseThrow());

        assertThat(users.findLanguage(userId)).contains(UiLanguage.EN);
    }
}
