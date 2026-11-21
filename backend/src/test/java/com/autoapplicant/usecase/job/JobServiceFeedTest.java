package com.autoapplicant.usecase.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.search.JobSearchQuery;
import com.autoapplicant.domain.user.UserPreferences;
import com.autoapplicant.port.out.job.IgnoredJobRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.job.SavedJobRepositoryPort;
import com.autoapplicant.port.out.user.PreferencesRepositoryPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

class JobServiceFeedTest {

    private static final int PAGE = 1;
    private static final int SIZE = 10;
    private static final long NOT_IGNORED_TOTAL = 42;

    private final JobRepositoryPort jobRepo = mock(JobRepositoryPort.class);
    private final IgnoredJobRepositoryPort ignoredJobRepo = mock(IgnoredJobRepositoryPort.class);
    private final PreferencesRepositoryPort prefsRepo = mock(PreferencesRepositoryPort.class);
    private final JobService service =
            new JobService(jobRepo, mock(SavedJobRepositoryPort.class), ignoredJobRepo, prefsRepo);

    @Test
    void a_users_feed_total_comes_from_the_same_predicate_as_its_pages() {
        UUID userId = UUID.randomUUID();
        Job job = Job.builder().id(UUID.randomUUID()).title("Dev").build();
        when(jobRepo.findActiveNotIgnoredBy(userId, List.of(), PAGE, SIZE)).thenReturn(List.of(job));
        when(jobRepo.countActiveNotIgnoredBy(userId, List.of())).thenReturn(NOT_IGNORED_TOTAL);

        Page<Job> page = service.getJobs(new JobSearchQuery(null, null, PAGE, SIZE, "postedAt", userId));

        assertThat(page.getContent()).containsExactly(job);
        assertThat(page.getTotalElements()).isEqualTo(NOT_IGNORED_TOTAL);
        verifyNoInteractions(ignoredJobRepo);
    }

    @Test
    void a_users_feed_leaves_out_the_categories_she_skips() {
        // Bug: the setting is saved but the feed never reads it, so skipped categories still
        // fill her feed.
        UUID userId = UUID.randomUUID();
        Job job = Job.builder().id(UUID.randomUUID()).title("Dev").build();
        UserPreferences prefs = new UserPreferences(null, userId, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, null,
                false, null, null, null, null, List.of("SALES"), null);
        when(prefsRepo.findByUserId(userId)).thenReturn(Optional.of(prefs));
        when(jobRepo.findActiveNotIgnoredBy(userId, List.of("SALES"), PAGE, SIZE)).thenReturn(List.of(job));
        when(jobRepo.countActiveNotIgnoredBy(userId, List.of("SALES"))).thenReturn(NOT_IGNORED_TOTAL);

        Page<Job> page = service.getJobs(new JobSearchQuery(null, null, PAGE, SIZE, "postedAt", userId));

        assertThat(page.getContent()).containsExactly(job);
        assertThat(page.getTotalElements()).isEqualTo(NOT_IGNORED_TOTAL);
    }

    @Test
    void an_anonymous_feed_counts_every_active_job() {
        when(jobRepo.findActive(PAGE, SIZE)).thenReturn(List.of());
        when(jobRepo.countActive()).thenReturn(NOT_IGNORED_TOTAL);

        Page<Job> page = service.getJobs(new JobSearchQuery(null, null, PAGE, SIZE, "postedAt"));

        assertThat(page.getTotalElements()).isEqualTo(NOT_IGNORED_TOTAL);
    }
}
