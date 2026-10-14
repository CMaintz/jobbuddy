package com.autoapplicant.usecase.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.application.Application;
import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.notification.Nudge;
import com.autoapplicant.domain.user.User;
import com.autoapplicant.domain.user.UserPreferences;
import com.autoapplicant.port.out.application.ApplicationRepositoryPort;
import com.autoapplicant.port.out.company.OutreachContactRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.notification.NotificationPort;
import com.autoapplicant.port.out.reminder.FollowUpReminderRepositoryPort;
import com.autoapplicant.port.out.user.PreferencesRepositoryPort;
import com.autoapplicant.port.out.user.UserRepositoryPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class DueReminderDigestServiceTest {

    private static final UUID USER = UUID.randomUUID();

    private final PreferencesRepositoryPort preferencesRepo = Mockito.mock(PreferencesRepositoryPort.class);
    private final UserRepositoryPort userRepo = Mockito.mock(UserRepositoryPort.class);
    private final DueNudgeFinder nudges = Mockito.mock(DueNudgeFinder.class);
    private final NotificationPort notification = Mockito.mock(NotificationPort.class);

    private final DueReminderDigestService service =
            new DueReminderDigestService(preferencesRepo, userRepo, nudges, notification);

    private static UserPreferences prefs(UUID userId) {
        return new UserPreferences(UUID.randomUUID(), userId, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, null,
                true, "DAILY", null, null, null);
    }

    @BeforeEach
    void setUp() {
        when(preferencesRepo.findAllWithNotificationsEnabled()).thenReturn(List.of(prefs(USER)));
        when(userRepo.findById(USER)).thenReturn(Optional.of(
                new User(USER, "user@example.dk", null, null, null, null, false, false, null, null)));
    }

    private static Nudge outreachDue(String company) {
        return new Nudge(Nudge.NudgeType.OUTREACH_FOLLOW_UP, null, null, UUID.randomUUID(), null,
                null, company, LocalDate.of(2026, 8, 26), null, "Mette Hansen");
    }

    private static Application application(ApplicationStatus status, UUID jobId, Instant appliedAt) {
        return new Application(UUID.randomUUID(), USER, jobId, status, appliedAt,
                null, null, null, null, null,
                null, null, null, null, null,
                appliedAt, appliedAt, null, null);
    }

    @Test
    void aDueFollowUpIsMailed() {
        when(nudges.findDueToday(USER)).thenReturn(List.of(outreachDue("Acme A/S")));

        assertThat(service.sendDueReminders()).isEqualTo(1);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        Mockito.verify(notification).send(Mockito.eq("user@example.dk"), anyString(), body.capture());
        assertThat(body.getValue()).contains("Acme A/S").contains("Mette Hansen");
    }

    @Test
    void nothingDueIsNotNews() {
        when(nudges.findDueToday(USER)).thenReturn(List.of());
        assertThat(service.sendDueReminders()).isZero();
        Mockito.verifyNoInteractions(notification);
    }

    @Test
    void onlyTheDatedTypesAreMailed() {
        // Deadlines and the 14-day heuristic already surface in the app and do not change hour to
        // hour; mailing them daily is how a digest gets filtered.
        Instant now = Instant.now();
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        Job job = Job.builder().id(UUID.randomUUID()).title("Backend developer").companyName("Acme")
                .applicationDeadline(LocalDate.now(clock).plusDays(2)).build();
        ApplicationRepositoryPort applicationRepo = Mockito.mock(ApplicationRepositoryPort.class);
        JobRepositoryPort jobRepo = Mockito.mock(JobRepositoryPort.class);
        FollowUpReminderRepositoryPort reminderRepo = Mockito.mock(FollowUpReminderRepositoryPort.class);
        OutreachContactRepositoryPort outreachRepo = Mockito.mock(OutreachContactRepositoryPort.class);
        when(applicationRepo.findByUserId(USER)).thenReturn(List.of(
                application(ApplicationStatus.SAVED, job.id(), null),
                application(ApplicationStatus.APPLIED, job.id(), now.minus(Duration.ofDays(20)))));
        when(jobRepo.findByIds(any())).thenReturn(List.of(job));
        when(reminderRepo.findDueByUserId(any(), any())).thenReturn(List.of());
        when(outreachRepo.findByUserId(USER)).thenReturn(List.of());
        DueNudgeFinder realFinder = new DueNudgeFinder(applicationRepo, jobRepo, reminderRepo, outreachRepo, clock);

        // The same pipeline does nudge in the app: a deadline is near and an application is unanswered.
        assertThat(new NudgeService(applicationRepo, jobRepo, realFinder, clock).getNudges(USER))
                .extracting(Nudge::type)
                .containsExactlyInAnyOrder(Nudge.NudgeType.DEADLINE_SOON, Nudge.NudgeType.FOLLOW_UP);

        DueReminderDigestService digest =
                new DueReminderDigestService(preferencesRepo, userRepo, realFinder, notification);
        assertThat(digest.sendDueReminders()).isZero();
        Mockito.verifyNoInteractions(notification);
    }

    @Test
    void theSubjectCountsWhatIsDue() {
        when(nudges.findDueToday(USER)).thenReturn(List.of(outreachDue("A"), outreachDue("B")));
        service.sendDueReminders();
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        Mockito.verify(notification).send(anyString(), subject.capture(), anyString());
        assertThat(subject.getValue()).isEqualTo("2 follow-ups due today");
    }

    @Test
    void aUserWithoutAnEmailIsSkippedRatherThanFailing() {
        when(userRepo.findById(USER)).thenReturn(Optional.of(
                new User(USER, null, null, null, null, null, false, false, null, null)));
        when(nudges.findDueToday(USER)).thenReturn(List.of(outreachDue("Acme")));

        assertThat(service.sendDueReminders()).isZero();
        Mockito.verifyNoInteractions(notification);
    }

    @Test
    void nobodyOptedInMeansNoWork() {
        when(preferencesRepo.findAllWithNotificationsEnabled()).thenReturn(List.of());
        assertThat(service.sendDueReminders()).isZero();
        Mockito.verifyNoInteractions(nudges);
    }
}
