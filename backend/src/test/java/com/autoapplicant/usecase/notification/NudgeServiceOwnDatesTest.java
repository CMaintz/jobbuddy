package com.autoapplicant.usecase.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.application.Application;
import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.notification.Nudge;
import com.autoapplicant.port.out.application.ApplicationRepositoryPort;
import com.autoapplicant.port.out.company.OutreachContactRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.reminder.FollowUpReminderRepositoryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** The in-app feed uses the deadline and interview the user entered, not just the crawled posting. */
class NudgeServiceOwnDatesTest {

    private static final UUID USER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-26T09:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 26);
    private static final LocalTime TEN = LocalTime.of(10, 0);

    private final ApplicationRepositoryPort applicationRepo = Mockito.mock(ApplicationRepositoryPort.class);
    private final JobRepositoryPort jobRepo = Mockito.mock(JobRepositoryPort.class);
    private final FollowUpReminderRepositoryPort reminderRepo = Mockito.mock(FollowUpReminderRepositoryPort.class);
    private final OutreachContactRepositoryPort outreachRepo = Mockito.mock(OutreachContactRepositoryPort.class);

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final NudgeService service = new NudgeService(applicationRepo, jobRepo,
            new DueNudgeFinder(applicationRepo, jobRepo, reminderRepo, outreachRepo, clock), clock);

    @BeforeEach
    void setUp() {
        when(reminderRepo.findDueByUserId(any(), any())).thenReturn(List.of());
        when(outreachRepo.findByUserId(USER)).thenReturn(List.of());
    }

    private static Job posting(LocalDate deadline) {
        return Job.builder().id(UUID.randomUUID()).title("Kommunikationsmedarbejder")
                .companyName("Acme A/S").applicationDeadline(deadline).build();
    }

    private static Application.Builder application(Job job, ApplicationStatus status) {
        return new Application.Builder().id(UUID.randomUUID()).userId(USER).jobId(job.id()).status(status);
    }

    private void pipeline(Job job, Application app) {
        when(applicationRepo.findByUserId(USER)).thenReturn(List.of(app));
        when(jobRepo.findByIds(any())).thenReturn(List.of(job));
    }

    @Test
    void aDeadlineTheUserEnteredNudgesWhenThePostingHasNone() {
        Job job = posting(null);
        pipeline(job, application(job, ApplicationStatus.SAVED).deadline(TODAY.plusDays(3)).build());

        assertThat(service.getNudges(USER)).singleElement().satisfies(nudge -> {
            assertThat(nudge.type()).isEqualTo(Nudge.NudgeType.DEADLINE_SOON);
            assertThat(nudge.deadline()).isEqualTo(TODAY.plusDays(3));
        });
    }

    @Test
    void thePostingDeadlineStillNudgesWhenTheUserEnteredNone() {
        Job job = posting(TODAY.plusDays(5));
        pipeline(job, application(job, ApplicationStatus.SAVED).build());

        assertThat(service.getNudges(USER)).extracting(Nudge::deadline).containsExactly(TODAY.plusDays(5));
    }

    @Test
    void theUsersOwnDeadlineWinsOverThePostings() {
        Job job = posting(TODAY.plusDays(2));
        pipeline(job, application(job, ApplicationStatus.PREPARING).deadline(TODAY.plusDays(6)).build());

        assertThat(service.getNudges(USER)).extracting(Nudge::deadline).containsExactly(TODAY.plusDays(6));
    }

    @Test
    void theUsersOwnLaterDeadlineSilencesAPostingDeadlineDueToday() {
        // The user knows the real deadline is next month; a nudge on the posting's date is noise.
        Job job = posting(TODAY);
        pipeline(job, application(job, ApplicationStatus.SAVED).deadline(TODAY.plusDays(30)).build());

        assertThat(service.getNudges(USER)).isEmpty();
    }

    @Test
    void anInterviewTodayIsANudge() {
        Job job = posting(null);
        pipeline(job, application(job, ApplicationStatus.INTERVIEW)
                .interviewAt(LocalDateTime.of(TODAY, TEN)).build());

        assertThat(service.getNudges(USER)).singleElement().satisfies(nudge -> {
            assertThat(nudge.type()).isEqualTo(Nudge.NudgeType.INTERVIEW_SOON);
            assertThat(nudge.deadline()).isEqualTo(TODAY);
            assertThat(nudge.time()).isEqualTo(TEN);
            assertThat(nudge.companyName()).isEqualTo("Acme A/S");
        });
    }

    @Test
    void anInterviewTomorrowIsANudge() {
        Job job = posting(null);
        pipeline(job, application(job, ApplicationStatus.RECRUITER_CONTACT)
                .interviewAt(LocalDateTime.of(TODAY.plusDays(1), TEN)).build());

        assertThat(service.getNudges(USER)).extracting(Nudge::type)
                .containsExactly(Nudge.NudgeType.INTERVIEW_SOON);
    }

    @Test
    void anInterviewThatHasPassedIsDropped() {
        Job job = posting(null);
        pipeline(job, application(job, ApplicationStatus.INTERVIEW)
                .interviewAt(LocalDateTime.of(TODAY.minusDays(1), TEN)).build());

        assertThat(service.getNudges(USER)).isEmpty();
    }

    @Test
    void anInterviewComesBeforeDeadlines() {
        // The dashboard shows only the first few nudges; an interview must not fall below them.
        Job interviewJob = posting(null);
        Job deadlineJob = posting(TODAY);
        when(applicationRepo.findByUserId(USER)).thenReturn(List.of(
                application(deadlineJob, ApplicationStatus.SAVED).build(),
                application(interviewJob, ApplicationStatus.INTERVIEW)
                        .interviewAt(LocalDateTime.of(TODAY.plusDays(1), TEN)).build()));
        when(jobRepo.findByIds(any())).thenReturn(List.of(interviewJob, deadlineJob));

        assertThat(service.getNudges(USER)).extracting(Nudge::type)
                .containsExactly(Nudge.NudgeType.INTERVIEW_SOON, Nudge.NudgeType.DEADLINE_SOON);
    }
}
