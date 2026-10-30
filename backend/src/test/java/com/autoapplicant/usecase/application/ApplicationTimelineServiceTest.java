package com.autoapplicant.usecase.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.domain.application.ApplicationStatusEvent;
import com.autoapplicant.domain.application.ApplicationTimelineEntry;
import com.autoapplicant.port.out.application.ApplicationStatusEventRepositoryPort;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

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
    void the_users_own_moves_are_on_the_timeline_with_their_notes_but_not_as_replies() {
        givenLedger(
                event(null, ApplicationStatus.SAVED, SAVED_AT, null),
                event(ApplicationStatus.PREPARING, ApplicationStatus.APPLIED, APPLIED_AT,
                        "Sent through their portal, referenced Marie"));

        List<ApplicationTimelineEntry> timeline = service.getTimeline(APPLICATION, USER);

        assertThat(timeline).extracting(ApplicationTimelineEntry::toStatus)
                .containsExactly(ApplicationStatus.SAVED, ApplicationStatus.APPLIED);
        assertThat(timeline).noneMatch(ApplicationTimelineEntry::employerResponse);
        assertThat(timeline.getFirst().fromStatus()).isNull();
        assertThat(timeline.getFirst().at()).isEqualTo(SAVED_AT);
        assertThat(timeline.getLast().fromStatus()).isEqualTo(ApplicationStatus.PREPARING);
        assertThat(timeline.getLast().notes()).isEqualTo("Sent through their portal, referenced Marie");
    }

    @Test
    void the_employers_replies_are_marked_as_theirs_and_keep_their_notes() {
        givenLedger(
                event(ApplicationStatus.APPLIED, ApplicationStatus.RECRUITER_CONTACT, SCREEN_AT,
                        "Called about the platform role"),
                event(ApplicationStatus.RECRUITER_CONTACT, ApplicationStatus.REJECTED, REJECTED_AT,
                        "Went with an internal candidate"));

        List<ApplicationTimelineEntry> timeline = service.getTimeline(APPLICATION, USER);

        assertThat(timeline).allMatch(ApplicationTimelineEntry::employerResponse);
        assertThat(timeline).extracting(ApplicationTimelineEntry::notes)
                .containsExactly("Called about the platform role", "Went with an internal candidate");
    }
}
