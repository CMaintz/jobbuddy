package com.autoapplicant.usecase.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.search.JobSearchQuery;
import com.autoapplicant.port.out.job.IgnoredJobRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.job.SavedJobRepositoryPort;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

class JobServiceFeedTest {

    private static final int PAGE = 1;
    private static final int SIZE = 10;
    private static final long NOT_IGNORED_TOTAL = 42;

    private final JobRepositoryPort jobRepo = mock(JobRepositoryPort.class);
    private final IgnoredJobRepositoryPort ignoredJobRepo = mock(IgnoredJobRepositoryPort.class);
    private final JobService service =
            new JobService(jobRepo, mock(SavedJobRepositoryPort.class), ignoredJobRepo);

    @Test
    void a_users_feed_total_comes_from_the_same_predicate_as_its_pages() {
        UUID userId = UUID.randomUUID();
        Job job = Job.builder().id(UUID.randomUUID()).title("Dev").build();
        when(jobRepo.findActiveNotIgnoredBy(userId, PAGE, SIZE)).thenReturn(List.of(job));
        when(jobRepo.countActiveNotIgnoredBy(userId)).thenReturn(NOT_IGNORED_TOTAL);

        Page<Job> page = service.getJobs(new JobSearchQuery(null, null, PAGE, SIZE, "postedAt", userId));

        assertThat(page.getContent()).containsExactly(job);
        assertThat(page.getTotalElements()).isEqualTo(NOT_IGNORED_TOTAL);
        verifyNoInteractions(ignoredJobRepo);
    }

    @Test
    void an_anonymous_feed_counts_every_active_job() {
        when(jobRepo.findActive(PAGE, SIZE)).thenReturn(List.of());
        when(jobRepo.countActive()).thenReturn(NOT_IGNORED_TOTAL);

        Page<Job> page = service.getJobs(new JobSearchQuery(null, null, PAGE, SIZE, "postedAt"));

        assertThat(page.getTotalElements()).isEqualTo(NOT_IGNORED_TOTAL);
    }
}
