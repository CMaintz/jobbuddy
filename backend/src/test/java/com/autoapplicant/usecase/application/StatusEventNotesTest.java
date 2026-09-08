package com.autoapplicant.usecase.application;

import com.autoapplicant.adapter.persistence.adapter.ApplicationStatusEventPersistenceAdapter;
import com.autoapplicant.adapter.persistence.entity.ApplicationStatusEventEntity;
import com.autoapplicant.adapter.persistence.repository.ApplicationStatusEventJpaRepository;
import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.domain.application.ApplicationStatusEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * A status event records a real move in an application, and the note is what the candidate
 * wrote about it. The note has been dropped by a mapper once already — accepted by the
 * domain record, never written, read back as null — which showed on the timeline as
 * something having happened but never what. Pin both directions.
 */
class StatusEventNotesTest {

    private final ApplicationStatusEventJpaRepository repo = mock(ApplicationStatusEventJpaRepository.class);
    private final ApplicationStatusEventPersistenceAdapter adapter =
            new ApplicationStatusEventPersistenceAdapter(repo);

    @Test
    void the_note_survives_the_round_trip() {
        String note = "Gik videre med en kandidat med mere Kubernetes-erfaring.";
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        adapter.save(new ApplicationStatusEvent(null, UUID.randomUUID(), UUID.randomUUID(),
                ApplicationStatus.APPLIED, ApplicationStatus.REJECTED, Instant.now(), note));

        ArgumentCaptor<ApplicationStatusEventEntity> captor =
                ArgumentCaptor.forClass(ApplicationStatusEventEntity.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getNotes()).isEqualTo(note);

        ApplicationStatusEvent readBack = adapter.save(new ApplicationStatusEvent(
                null, UUID.randomUUID(), UUID.randomUUID(),
                ApplicationStatus.APPLIED, ApplicationStatus.REJECTED, Instant.now(), note));
        assertThat(readBack.notes()).isEqualTo(note);
    }

    @Test
    void a_move_with_nothing_to_say_carries_no_note() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApplicationStatusEvent saved = adapter.save(new ApplicationStatusEvent(
                null, UUID.randomUUID(), UUID.randomUUID(),
                null, ApplicationStatus.SAVED, Instant.now(), null));

        assertThat(saved.notes()).isNull();
    }

    @Test
    void the_stages_the_employer_drives_are_exactly_the_ones_that_read_as_their_reply() {
        assertThat(ApplicationStatus.RECRUITER_CONTACT.isEmployerDriven()).isTrue();
        assertThat(ApplicationStatus.INTERVIEW.isEmployerDriven()).isTrue();
        assertThat(ApplicationStatus.TECHNICAL_TEST.isEmployerDriven()).isTrue();
        assertThat(ApplicationStatus.FINAL_ROUND.isEmployerDriven()).isTrue();
        assertThat(ApplicationStatus.OFFER.isEmployerDriven()).isTrue();
        assertThat(ApplicationStatus.REJECTED.isEmployerDriven()).isTrue();

        assertThat(ApplicationStatus.SAVED.isEmployerDriven()).isFalse();
        assertThat(ApplicationStatus.PREPARING.isEmployerDriven()).isFalse();
        assertThat(ApplicationStatus.APPLIED.isEmployerDriven()).isFalse();
        assertThat(ApplicationStatus.ARCHIVED.isEmployerDriven()).isFalse();
    }
}
