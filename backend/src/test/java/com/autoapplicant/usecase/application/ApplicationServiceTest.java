package com.autoapplicant.usecase.application;

import static com.autoapplicant.domain.application.ApplicationStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.autoapplicant.domain.application.Application;
import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.domain.application.ApplicationStatusEvent;
import com.autoapplicant.domain.application.CreateApplicationCommand;
import com.autoapplicant.port.out.application.ApplicationRepositoryPort;
import com.autoapplicant.port.out.application.ApplicationStatusEventRepositoryPort;
import com.autoapplicant.port.out.document.GeneratedDocumentRepositoryPort;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    @Mock ApplicationRepositoryPort repo;
    @Mock ApplicationStatusEventRepositoryPort statusEventRepo;
    @Mock GeneratedDocumentRepositoryPort generatedDocuments;

    ApplicationService service;

    UUID userId = UUID.randomUUID();
    UUID jobId  = UUID.randomUUID();
    UUID appId  = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ApplicationService(repo, statusEventRepo, generatedDocuments);
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
        assertThat(cap.getValue().appliedAt()).isNull();
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
    void a_second_application_for_the_same_job_is_refused() {
        when(repo.existsByUserIdAndJobId(userId, jobId)).thenReturn(true);

        assertThatThrownBy(() -> service.createApplication(userId, jobId, null, null))
                .isInstanceOf(IllegalStateException.class);
        verify(repo, never()).save(any());
        verifyNoInteractions(statusEventRepo);
    }

    @Test
    void creation_opens_the_ledger_with_a_move_from_nowhere() {
        when(repo.save(any())).thenAnswer(inv -> withId(inv.getArgument(0)));

        Instant before = Instant.now();
        service.createApplication(userId, jobId, null, "Found it via a referral");

        ApplicationStatusEvent event = recordedEvent();
        assertThat(event.applicationId()).isEqualTo(appId);
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.fromStatus()).isNull();
        assertThat(event.toStatus()).isEqualTo(SAVED);
        assertThat(event.notes()).isEqualTo("Found it via a referral");
        assertThat(event.occurredAt()).isBetween(before, Instant.now());
    }

    @Test
    void creating_an_application_already_sent_stamps_when_it_was_applied() {
        when(repo.save(any())).thenAnswer(inv -> withId(inv.getArgument(0)));

        Application saved = service.createApplication(new CreateApplicationCommand(
                userId, jobId, null, null, null, APPLIED, null, null, null, null, null));

        assertThat(saved.status()).isEqualTo(APPLIED);
        assertThat(saved.appliedAt()).isNotNull();
        assertThat(recordedEvent().occurredAt()).isEqualTo(saved.appliedAt());
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
    void a_status_change_is_written_to_the_ledger_at_the_moment_it_was_applied() {
        when(repo.findByIdAndUserId(appId, userId))
                .thenReturn(Optional.of(application(appId, userId, jobId, PREPARING, null)));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Application result = service.updateStatus(appId, userId, APPLIED, "Sent through their portal");

        ApplicationStatusEvent event = recordedEvent();
        assertThat(event.applicationId()).isEqualTo(appId);
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.fromStatus()).isEqualTo(PREPARING);
        assertThat(event.toStatus()).isEqualTo(APPLIED);
        assertThat(event.notes()).isEqualTo("Sent through their portal");
        assertThat(event.occurredAt()).isEqualTo(result.appliedAt()).isEqualTo(result.updatedAt());
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
        Application existing = application(appId, userId, jobId, SAVED, null).toBuilder()
                .notes("original note").build();
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
    void an_invalid_transition_is_refused_and_leaves_no_trace() {
        Application existing = application(appId, userId, jobId, REJECTED, null);
        when(repo.findByIdAndUserId(appId, userId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.updateStatus(appId, userId, ARCHIVED, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REJECTED");
        verify(repo, never()).save(any());
        verifyNoInteractions(statusEventRepo);
    }

    @Test
    void update_status_throws_when_skipping_stages() {
        Application existing = application(appId, userId, jobId, SAVED, null);
        when(repo.findByIdAndUserId(appId, userId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.updateStatus(appId, userId, OFFER, null))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── recruiter info and outcome ────────────────────────────────────────────

    @Test
    void recruiter_info_fields_left_null_keep_what_was_there() {
        Application existing = application(appId, userId, jobId, RECRUITER_CONTACT, null).toBuilder()
                .recruiterName("Mette Hansen").recruiterEmail("mette@acme.dk")
                .recruiterMessage("Hej Mette").build();
        when(repo.findByIdAndUserId(appId, userId)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Application result = service.updateRecruiterInfo(appId, userId,
                null, "mette.hansen@acme.dk", null, "Tak, vi ringer");

        assertThat(result.recruiterName()).isEqualTo("Mette Hansen");
        assertThat(result.recruiterEmail()).isEqualTo("mette.hansen@acme.dk");
        assertThat(result.recruiterMessage()).isEqualTo("Hej Mette");
        assertThat(result.recruiterReply()).isEqualTo("Tak, vi ringer");
    }

    @Test
    void an_outcome_field_left_null_keeps_what_was_there() {
        Application existing = application(appId, userId, jobId, REJECTED, null).toBuilder()
                .outcomeFeedback("Wanted more Kubernetes").outcomeLessons("Lead with Kafka").build();
        when(repo.findByIdAndUserId(appId, userId)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Application result = service.updateOutcome(appId, userId, null, "Lead with the platform work");

        assertThat(result.outcomeFeedback()).isEqualTo("Wanted more Kubernetes");
        assertThat(result.outcomeLessons()).isEqualTo("Lead with the platform work");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ApplicationStatusEvent recordedEvent() {
        ArgumentCaptor<ApplicationStatusEvent> cap = ArgumentCaptor.forClass(ApplicationStatusEvent.class);
        verify(statusEventRepo).save(cap.capture());
        return cap.getValue();
    }

    private Application withId(Application unsaved) {
        return unsaved.toBuilder().id(appId).build();
    }

    private Application application(UUID id, UUID userId, UUID jobId,
                                    ApplicationStatus status, Instant appliedAt) {
        return new Application(id, userId, jobId, status, appliedAt,
                null, null, null, null, null,
                null, null, null, null, null,
                Instant.now(), Instant.now(), null, null);
    }
}
