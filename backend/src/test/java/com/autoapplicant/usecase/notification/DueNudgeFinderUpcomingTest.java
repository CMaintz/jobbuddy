package com.autoapplicant.usecase.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.application.Application;
import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.notification.UpcomingDate;
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

/** The user's own deadlines and interviews that the daily email warns about. */
class DueNudgeFinderUpcomingTest {

    private static final UUID USER = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);
    private static final Clock CLOCK =
            Clock.fixed(TODAY.atTime(7, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    private final ApplicationRepositoryPort applicationRepo = Mockito.mock(ApplicationRepositoryPort.class);
    private final JobRepositoryPort jobRepo = Mockito.mock(JobRepositoryPort.class);
    private final DueNudgeFinder finder = new DueNudgeFinder(applicationRepo, jobRepo,
            Mockito.mock(FollowUpReminderRepositoryPort.class),
            Mockito.mock(OutreachContactRepositoryPort.class), CLOCK);

    private final Job job = Job.builder().id(UUID.randomUUID())
            .title("Kommunikationsmedarbejder").companyName("Acme A/S").build();

    @BeforeEach
    void setUp() {
        when(jobRepo.findByIds(any())).thenReturn(List.of(job));
    }

    private Application app(ApplicationStatus status, LocalDate deadline, LocalDateTime interviewAt) {
        Instant created = TODAY.minusDays(10).atStartOfDay().toInstant(ZoneOffset.UTC);
        return new Application(UUID.randomUUID(), USER, job.id(), status, null,
                null, null, null, null, null,
                null, null, null, null, null,
                created, created, null, null, deadline, interviewAt);
    }

    private List<UpcomingDate> upcoming(Application... apps) {
        when(applicationRepo.findByUserId(USER)).thenReturn(List.of(apps));
        return finder.findUpcoming(USER);
    }

    @Test
    void anOwnDeadlineInTwoDaysIsUpcomingWithItsJob() {
        assertThat(upcoming(app(ApplicationStatus.PREPARING, TODAY.plusDays(2), null)))
                .singleElement()
                .satisfies(d -> {
                    assertThat(d.kind()).isEqualTo(UpcomingDate.Kind.DEADLINE);
                    assertThat(d.date()).isEqualTo(TODAY.plusDays(2));
                    assertThat(d.jobTitle()).isEqualTo("Kommunikationsmedarbejder");
                    assertThat(d.companyName()).isEqualTo("Acme A/S");
                });
    }

    @Test
    void aDeadlineFurtherOutIsNotYetNews() {
        assertThat(upcoming(app(ApplicationStatus.SAVED, TODAY.plusDays(5), null))).isEmpty();
    }

    @Test
    void aDeadlineStopsMatteringOnceTheApplicationIsSent() {
        assertThat(upcoming(app(ApplicationStatus.APPLIED, TODAY.plusDays(1), null))).isEmpty();
    }

    @Test
    void tomorrowsInterviewKeepsTheTimeTheUserTyped() {
        LocalDateTime interview = TODAY.plusDays(1).atTime(9, 0);

        assertThat(upcoming(app(ApplicationStatus.INTERVIEW, null, interview)))
                .singleElement()
                .satisfies(d -> {
                    assertThat(d.kind()).isEqualTo(UpcomingDate.Kind.INTERVIEW);
                    assertThat(d.date()).isEqualTo(TODAY.plusDays(1));
                    assertThat(d.time()).isEqualTo(LocalTime.of(9, 0));
                });
    }

    @Test
    void anInterviewThatHasPassedIsLeftOut() {
        assertThat(upcoming(app(ApplicationStatus.INTERVIEW, null, TODAY.minusDays(1).atTime(9, 0))))
                .isEmpty();
    }
}
