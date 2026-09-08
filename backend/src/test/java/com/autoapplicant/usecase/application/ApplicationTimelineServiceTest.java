package com.autoapplicant.usecase.application;

import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.domain.application.ApplicationStatusEvent;
import com.autoapplicant.domain.application.ApplicationTimelineEntry;
import com.autoapplicant.port.out.application.ApplicationStatusEventRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ApplicationTimelineServiceTest {

    private static final UUID APPLICATION = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    private static final Instant SAVED_AT = Instant.parse("2026-02-01T10:00:00Z");
    private static final Instant APPLIED_AT = Instant.parse("2026-02-04T08:30:00Z");
    private static final Instant SCREEN_AT = Instant.parse("2026-02-11T14:00:00Z");
    private static final Instant REJECTED_AT = Instant.parse("2026-03-01T09:00:00Z");

    private final ApplicationStatusEventRepositoryPort statusEvents =
            mock(ApplicationStatusEventRepositoryPort.class);
    private final ApplicationTimelineService service = new ApplicationTimelineService(statusEvents);

    private void givenLedger(ApplicationStatusEvent... events) {
        when(statusEvents.findByApplicationIdAndUserId(APPLICATION, USER)).thenReturn(List.of(events));
    }

    private static ApplicationStatusEvent event(ApplicationStatus from, ApplicationStatus to,
                                                Instant at, String notes) {
        return new ApplicationStatusEvent(UUID.randomUUID(), APPLICATION, USER, from, to, at, notes);
    }

    @Test
    void the_users_own_moves_are_on_the_timeline_not_just_the_employers_replies() {
        givenLedger(
                event(null, ApplicationStatus.SAVED, SAVED_AT, null),
                event(ApplicationStatus.PREPARING, ApplicationStatus.APPLIED, APPLIED_AT, null));

        List<ApplicationTimelineEntry> timeline = service.getTimeline(APPLICATION, USER);

        assertThat(timeline).extracting(ApplicationTimelineEntry::toStatus)
                .containsExactly(ApplicationStatus.SAVED, ApplicationStatus.APPLIED);
        assertThat(timeline).noneMatch(ApplicationTimelineEntry::employerResponse);
        assertThat(timeline.getFirst().fromStatus()).isNull();
    }

    @Test
    void an_employers_reply_carries_its_note_and_is_marked_as_theirs() {
        givenLedger(
                event(null, ApplicationStatus.SAVED, SAVED_AT, null),
                event(ApplicationStatus.APPLIED, ApplicationStatus.RECRUITER_CONTACT, SCREEN_AT,
                        "Called about the platform role"));

        List<ApplicationTimelineEntry> timeline = service.getTimeline(APPLICATION, USER);

        assertThat(timeline.getFirst().employerResponse()).isFalse();
        assertThat(timeline.getFirst().notes()).isNull();
        assertThat(timeline.getLast().employerResponse()).isTrue();
        assertThat(timeline.getLast().notes()).isEqualTo("Called about the platform role");
    }

    @Test
    void a_note_on_the_users_own_move_is_kept_even_though_it_is_not_a_reply() {
        givenLedger(event(ApplicationStatus.PREPARING, ApplicationStatus.APPLIED, APPLIED_AT,
                "Sent through their portal, referenced Marie"));

        ApplicationTimelineEntry entry = service.getTimeline(APPLICATION, USER).getFirst();

        assertThat(entry.employerResponse()).isFalse();
        assertThat(entry.notes()).isEqualTo("Sent through their portal, referenced Marie");
    }

    @Test
    void a_rejection_is_the_employers_and_keeps_the_reason() {
        givenLedger(event(ApplicationStatus.RECRUITER_CONTACT, ApplicationStatus.REJECTED, REJECTED_AT,
                "Went with an internal candidate"));

        ApplicationTimelineEntry entry = service.getTimeline(APPLICATION, USER).getFirst();

        assertThat(entry.employerResponse()).isTrue();
        assertThat(entry.notes()).isEqualTo("Went with an internal candidate");
    }

    @Test
    void another_users_application_id_yields_nothing_rather_than_their_history() {
        UUID theirs = UUID.randomUUID();
        when(statusEvents.findByApplicationIdAndUserId(theirs, USER)).thenReturn(List.of());

        assertThat(service.getTimeline(theirs, USER)).isEmpty();
        verify(statusEvents).findByApplicationIdAndUserId(theirs, USER);
    }
}
