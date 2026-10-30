package com.autoapplicant.adapter.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autoapplicant.adapter.persistence.entity.ApplicationStatusEventEntity;
import com.autoapplicant.adapter.persistence.repository.ApplicationStatusEventJpaRepository;
import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.domain.application.ApplicationStatusEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * A status event records a real move in an application, and the note is what the candidate
 * wrote about it. The note has been dropped by this mapper once already: accepted by the
 * domain record, never written, read back as null. The timeline then showed something
 * having happened but never what. Pin both directions.
 */
class ApplicationStatusEventPersistenceAdapterTest {

    private final ApplicationStatusEventJpaRepository repo = mock(ApplicationStatusEventJpaRepository.class);
    private final ApplicationStatusEventPersistenceAdapter adapter =
            new ApplicationStatusEventPersistenceAdapter(repo);

    @Test
    void the_note_and_the_move_survive_the_round_trip() {
        String note = "Gik videre med en kandidat med mere Kubernetes-erfaring.";
        Instant at = Instant.parse("2026-03-01T09:00:00Z");
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApplicationStatusEvent readBack = adapter.save(new ApplicationStatusEvent(null, UUID.randomUUID(),
                UUID.randomUUID(), ApplicationStatus.APPLIED, ApplicationStatus.REJECTED, at, note));

        ArgumentCaptor<ApplicationStatusEventEntity> captor =
                ArgumentCaptor.forClass(ApplicationStatusEventEntity.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getNotes()).isEqualTo(note);
        assertThat(captor.getValue().getFromStatus()).isEqualTo("APPLIED");
        assertThat(captor.getValue().getToStatus()).isEqualTo("REJECTED");
        assertThat(readBack.notes()).isEqualTo(note);
        assertThat(readBack.occurredAt()).isEqualTo(at);
    }

    @Test
    void an_applications_opening_event_has_no_from_status_and_no_note() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApplicationStatusEvent saved = adapter.save(new ApplicationStatusEvent(
                null, UUID.randomUUID(), UUID.randomUUID(),
                null, ApplicationStatus.SAVED, Instant.now(), null));

        assertThat(saved.fromStatus()).isNull();
        assertThat(saved.toStatus()).isEqualTo(ApplicationStatus.SAVED);
        assertThat(saved.notes()).isNull();
    }
}
