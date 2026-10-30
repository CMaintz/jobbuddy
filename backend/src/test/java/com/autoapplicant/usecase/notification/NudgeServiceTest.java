package com.autoapplicant.usecase.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.application.Application;
import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.domain.company.OutreachContact;
import com.autoapplicant.domain.company.OutreachStatus;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.notification.Nudge;
import com.autoapplicant.domain.reminder.FollowUpReminder;
import com.autoapplicant.port.out.application.ApplicationRepositoryPort;
import com.autoapplicant.port.out.company.OutreachContactRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.reminder.FollowUpReminderRepositoryPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class NudgeServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-26T09:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 26);

    private final ApplicationRepositoryPort applicationRepo = Mockito.mock(ApplicationRepositoryPort.class);
    private final JobRepositoryPort jobRepo = Mockito.mock(JobRepositoryPort.class);
    private final FollowUpReminderRepositoryPort reminderRepo = Mockito.mock(FollowUpReminderRepositoryPort.class);
    private final OutreachContactRepositoryPort outreachRepo = Mockito.mock(OutreachContactRepositoryPort.class);

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final NudgeService service = new NudgeService(applicationRepo, jobRepo,
            new DueNudgeFinder(applicationRepo, jobRepo, reminderRepo, outreachRepo, clock), clock);

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

    private static final Duration FOLLOW_UP_AFTER = Duration.ofDays(14);

    private static Job jobWithDeadline(LocalDate deadline) {
        return Job.builder().id(UUID.randomUUID()).title("Backend Developer")
                .companyName("Acme A/S").applicationDeadline(deadline).build();
    }

    private static Application application(Job job, ApplicationStatus status, Instant appliedAt,
                                           String recruiterReply) {
        return new Application.Builder().id(UUID.randomUUID()).userId(USER).jobId(job.id())
                .status(status).appliedAt(appliedAt).recruiterReply(recruiterReply).build();
    }

    private void pipeline(List<Job> jobs, List<Application> apps) {
        when(applicationRepo.findByUserId(USER)).thenReturn(apps);
        when(jobRepo.findByIds(any())).thenReturn(jobs);
    }

    @Test
    void theDeadlineWindowRunsFromTodayThroughSevenDaysOut() {
        List<Job> jobs = List.of(jobWithDeadline(TODAY.minusDays(1)), jobWithDeadline(TODAY),
                jobWithDeadline(TODAY.plusDays(7)), jobWithDeadline(TODAY.plusDays(8)));
        pipeline(jobs, jobs.stream()
                .map(job -> application(job, ApplicationStatus.SAVED, null, null)).toList());

        assertThat(service.getNudges(USER))
                .allSatisfy(nudge -> assertThat(nudge.type()).isEqualTo(Nudge.NudgeType.DEADLINE_SOON))
                .extracting(Nudge::deadline)
                .containsExactly(TODAY, TODAY.plusDays(7));
    }

    @Test
    void aDeadlineIsNoLongerANudgeOnceTheApplicationIsSent() {
        Job job = jobWithDeadline(TODAY.plusDays(2));
        pipeline(List.of(job), List.of(application(job, ApplicationStatus.APPLIED, NOW, null)));

        assertThat(service.getNudges(USER)).isEmpty();
    }

    @Test
    void aFollowUpIsDueFourteenDaysAfterApplyingAndNotBefore() {
        // Counted against the injected clock: against the wall clock the boundary drifts with
        // the date the code runs, and a test can only pin it by accident.
        Job onTheDay = jobWithDeadline(null);
        Job aSecondShort = jobWithDeadline(null);
        pipeline(List.of(onTheDay, aSecondShort), List.of(
                application(onTheDay, ApplicationStatus.APPLIED, NOW.minus(FOLLOW_UP_AFTER), null),
                application(aSecondShort, ApplicationStatus.APPLIED,
                        NOW.minus(FOLLOW_UP_AFTER).plusSeconds(1), null)));

        assertThat(service.getNudges(USER)).singleElement().satisfies(nudge -> {
            assertThat(nudge.type()).isEqualTo(Nudge.NudgeType.FOLLOW_UP);
            assertThat(nudge.jobId()).isEqualTo(onTheDay.id());
            assertThat(nudge.daysSinceApplied()).isEqualTo(14);
        });
    }

    @Test
    void aRecruiterReplySilencesTheFollowUpButABlankOneDoesNot() {
        Job replied = jobWithDeadline(null);
        Job blank = jobWithDeadline(null);
        Instant longAgo = NOW.minus(FOLLOW_UP_AFTER).minus(FOLLOW_UP_AFTER);
        pipeline(List.of(replied, blank), List.of(
                application(replied, ApplicationStatus.APPLIED, longAgo, "Tak, vi vender tilbage"),
                application(blank, ApplicationStatus.APPLIED, longAgo, "   ")));

        assertThat(service.getNudges(USER)).extracting(Nudge::jobId).containsExactly(blank.id());
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
