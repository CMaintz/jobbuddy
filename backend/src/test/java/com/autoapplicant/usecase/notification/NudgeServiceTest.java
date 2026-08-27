package com.autoapplicant.usecase.notification;

import com.autoapplicant.domain.company.OutreachContact;
import com.autoapplicant.domain.company.OutreachStatus;
import com.autoapplicant.domain.notification.Nudge;
import com.autoapplicant.domain.reminder.FollowUpReminder;
import com.autoapplicant.port.out.application.ApplicationRepositoryPort;
import com.autoapplicant.port.out.company.OutreachContactRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.reminder.FollowUpReminderRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class NudgeServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-26T09:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 26);

    private final ApplicationRepositoryPort applicationRepo = Mockito.mock(ApplicationRepositoryPort.class);
    private final JobRepositoryPort jobRepo = Mockito.mock(JobRepositoryPort.class);
    private final FollowUpReminderRepositoryPort reminderRepo = Mockito.mock(FollowUpReminderRepositoryPort.class);
    private final OutreachContactRepositoryPort outreachRepo = Mockito.mock(OutreachContactRepositoryPort.class);

    private final NudgeService service = new NudgeService(applicationRepo, jobRepo, reminderRepo,
            outreachRepo, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(applicationRepo.findByUserId(USER)).thenReturn(List.of());
        when(jobRepo.findByIds(any())).thenReturn(List.of());
        when(reminderRepo.findDueByUserId(any(), any())).thenReturn(List.of());
        when(outreachRepo.findByUserId(USER)).thenReturn(List.of());
    }

    private static OutreachContact outreach(LocalDate followUpDue, OutreachStatus status) {
        return new OutreachContact(UUID.randomUUID(), USER, UUID.randomUUID(), "Acme A/S",
                status, "EMAIL", "Mette Hansen", NOW, followUpDue, null, NOW, NOW);
    }

    private static FollowUpReminder reminder(boolean completed) {
        return new FollowUpReminder(UUID.randomUUID(), null, USER, "Ring til Mette",
                NOW.minusSeconds(3600), completed, null, NOW, NOW);
    }

    @Test
    void anOutreachFollowUpThatHasArrivedBecomesANudge() {
        when(outreachRepo.findByUserId(USER)).thenReturn(List.of(outreach(TODAY, OutreachStatus.CONTACTED)));

        assertThat(service.getNudges(USER)).singleElement().satisfies(nudge -> {
            assertThat(nudge.type()).isEqualTo(Nudge.NudgeType.OUTREACH_FOLLOW_UP);
            assertThat(nudge.companyName()).isEqualTo("Acme A/S");
            assertThat(nudge.note()).isEqualTo("Mette Hansen");
        });
    }

    @Test
    void aFollowUpStillInTheFutureIsNotDue() {
        when(outreachRepo.findByUserId(USER))
                .thenReturn(List.of(outreach(TODAY.plusDays(3), OutreachStatus.CONTACTED)));
        assertThat(service.getNudges(USER)).isEmpty();
    }

    @Test
    void aClosedThreadNeverNudges() {
        when(outreachRepo.findByUserId(USER))
                .thenReturn(List.of(outreach(TODAY.minusDays(5), OutreachStatus.CLOSED)));
        assertThat(service.getNudges(USER)).isEmpty();
    }

    @Test
    void aDueReminderSurfacesWithoutOpeningTheApplication() {
        when(reminderRepo.findDueByUserId(any(), any())).thenReturn(List.of(reminder(false)));

        assertThat(service.getNudges(USER)).singleElement().satisfies(nudge -> {
            assertThat(nudge.type()).isEqualTo(Nudge.NudgeType.REMINDER_DUE);
            assertThat(nudge.note()).isEqualTo("Ring til Mette");
        });
    }

    @Test
    void aCompletedReminderIsDoneWith() {
        when(reminderRepo.findDueByUserId(any(), any())).thenReturn(List.of(reminder(true)));
        assertThat(service.getNudges(USER)).isEmpty();
    }

    @Test
    void whatTheUserAskedToBeRemindedOfComesFirst() {
        when(reminderRepo.findDueByUserId(any(), any())).thenReturn(List.of(reminder(false)));
        when(outreachRepo.findByUserId(USER)).thenReturn(List.of(outreach(TODAY, OutreachStatus.CONTACTED)));

        // They chose the date, so it outranks anything the app inferred.
        assertThat(service.getNudges(USER)).extracting(Nudge::type)
                .containsExactly(Nudge.NudgeType.REMINDER_DUE, Nudge.NudgeType.OUTREACH_FOLLOW_UP);
    }

    @Test
    void anEmptyPipelineProducesNothing() {
        assertThat(service.getNudges(USER)).isEmpty();
    }
}
