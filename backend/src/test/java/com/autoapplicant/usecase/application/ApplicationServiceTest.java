package com.autoapplicant.usecase.application;

import com.autoapplicant.domain.application.Application;
import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.port.out.analytics.ResponseMetricRepositoryPort;
import com.autoapplicant.port.out.application.ApplicationRepositoryPort;
import com.autoapplicant.usecase.document.StructuredGeneratedDocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.autoapplicant.domain.application.ApplicationStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    @Mock ApplicationRepositoryPort repo;
    @Mock ResponseMetricRepositoryPort responseMetricRepo;
    @Mock StructuredGeneratedDocumentService structuredGeneratedDocuments;

    ApplicationService service;

    UUID userId = UUID.randomUUID();
    UUID jobId  = UUID.randomUUID();
    UUID appId  = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ApplicationService(repo, responseMetricRepo, structuredGeneratedDocuments);
    }

    // ── createApplication ─────────────────────────────────────────────────────

    @Test
    void create_sets_initial_status_to_saved() {
        Application saved = application(appId, userId, jobId, SAVED, null);
        when(repo.save(any())).thenReturn(saved);

        Application result = service.createApplication(userId, jobId, null, null);

        ArgumentCaptor<Application> cap = ArgumentCaptor.forClass(Application.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().status()).isEqualTo(SAVED);
        assertThat(result).isSameAs(saved);
    }

    @Test
    void create_passes_cv_version_id_and_notes() {
        UUID cvId = UUID.randomUUID();
        Application saved = application(appId, userId, jobId, SAVED, null);
        when(repo.save(any())).thenReturn(saved);

        service.createApplication(userId, jobId, cvId, "some notes");

        ArgumentCaptor<Application> cap = ArgumentCaptor.forClass(Application.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().cvVersionId()).isEqualTo(cvId);
        assertThat(cap.getValue().notes()).isEqualTo("some notes");
    }

    @Test
    void create_passes_null_id_for_repo_to_assign() {
        Application saved = application(appId, userId, jobId, SAVED, null);
        when(repo.save(any())).thenReturn(saved);
        service.createApplication(userId, jobId, null, null);

        ArgumentCaptor<Application> cap = ArgumentCaptor.forClass(Application.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().id()).isNull();
    }

    // ── updateStatus ──────────────────────────────────────────────────────────

    @Test
    void update_status_persists_new_status() {
        Application existing = application(appId, userId, jobId, SAVED, null);
        Application updated  = application(appId, userId, jobId, PREPARING, null);
        when(repo.findByIdAndUserId(appId, userId)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenReturn(updated);

        Application result = service.updateStatus(appId, userId, PREPARING, null);

        ArgumentCaptor<Application> cap = ArgumentCaptor.forClass(Application.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().status()).isEqualTo(PREPARING);
        assertThat(result).isSameAs(updated);
    }

    @Test
    void update_status_sets_applied_at_when_transitioning_to_applied() {
        Application existing = application(appId, userId, jobId, PREPARING, null);
        when(repo.findByIdAndUserId(appId, userId)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Instant before = Instant.now();
        Application result = service.updateStatus(appId, userId, APPLIED, null);
        Instant after = Instant.now();

        assertThat(result.appliedAt()).isNotNull();
        assertThat(result.appliedAt()).isBetween(before, after);
    }

    @Test
    void update_status_does_not_overwrite_applied_at_for_non_applied_transitions() {
        Instant originalAppliedAt = Instant.parse("2024-01-01T10:00:00Z");
        Application existing = application(appId, userId, jobId, APPLIED, originalAppliedAt);
        when(repo.findByIdAndUserId(appId, userId)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Application result = service.updateStatus(appId, userId, RECRUITER_CONTACT, null);

        assertThat(result.appliedAt()).isEqualTo(originalAppliedAt);
    }

    @Test
    void update_status_overrides_notes_when_provided() {
        Application existing = application(appId, userId, jobId, SAVED, null);
        when(repo.findByIdAndUserId(appId, userId)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Application result = service.updateStatus(appId, userId, PREPARING, "new note");
        assertThat(result.notes()).isEqualTo("new note");
    }

    @Test
    void update_status_keeps_existing_notes_when_null_provided() {
        Application existing = applicationWithNotes(appId, userId, jobId, SAVED, "original note");
        when(repo.findByIdAndUserId(appId, userId)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Application result = service.updateStatus(appId, userId, PREPARING, null);
        assertThat(result.notes()).isEqualTo("original note");
    }

    @Test
    void update_status_throws_when_application_not_found() {
        when(repo.findByIdAndUserId(appId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(appId, userId, PREPARING, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void update_status_throws_on_invalid_transition() {
        Application existing = application(appId, userId, jobId, REJECTED, null);
        when(repo.findByIdAndUserId(appId, userId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.updateStatus(appId, userId, ARCHIVED, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REJECTED");
    }

    @Test
    void update_status_throws_when_skipping_stages() {
        Application existing = application(appId, userId, jobId, SAVED, null);
        when(repo.findByIdAndUserId(appId, userId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.updateStatus(appId, userId, OFFER, null))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── getApplications / getApplicationById ──────────────────────────────────

    @Test
    void get_applications_delegates_to_repo() {
        List<Application> list = List.of(application(appId, userId, jobId, SAVED, null));
        when(repo.findByUserId(userId)).thenReturn(list);

        assertThat(service.getApplications(userId)).isSameAs(list);
    }

    @Test
    void get_application_by_id_returns_empty_when_not_found() {
        when(repo.findByIdAndUserId(appId, userId)).thenReturn(Optional.empty());
        assertThat(service.getApplicationById(appId, userId)).isEmpty();
    }

    @Test
    void get_application_by_id_returns_present_when_found() {
        Application app = application(appId, userId, jobId, SAVED, null);
        when(repo.findByIdAndUserId(appId, userId)).thenReturn(Optional.of(app));
        assertThat(service.getApplicationById(appId, userId)).contains(app);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Application application(UUID id, UUID userId, UUID jobId,
                                    ApplicationStatus status, Instant appliedAt) {
        return new Application(id, userId, jobId, status, appliedAt,
                null, null, null, null, null,
                null, null, null, null, null,
                Instant.now(), Instant.now(), null, null);
    }

    private Application applicationWithNotes(UUID id, UUID userId, UUID jobId,
                                             ApplicationStatus status, String notes) {
        return new Application(id, userId, jobId, status, null,
                null, null, null, null, null,
                null, null, null, null, notes,
                Instant.now(), Instant.now(), null, null);
    }
}
