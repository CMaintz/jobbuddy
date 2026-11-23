package com.autoapplicant.usecase.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.JobCategory;
import com.autoapplicant.domain.search.JobSearchQuery;
import com.autoapplicant.domain.search.JobSearchResult;
import com.autoapplicant.domain.user.UserPreferences;
import com.autoapplicant.port.out.ai.AiProviderPort;
import com.autoapplicant.port.out.job.JobEmbeddingRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.job.JobSearchPort;
import com.autoapplicant.port.out.user.PreferencesRepositoryPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Search is the one way into the job list that did not read her skip list. */
class SearchSkipListServicesTest {

    private final UUID user = UUID.randomUUID();
    private final PreferencesRepositoryPort prefsRepo = mock(PreferencesRepositoryPort.class);

    SearchSkipListServicesTest() {
        UserPreferences skipsSales = new UserPreferences(null, user, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, null,
                false, null, null, null, null, List.of("SALES"), null);
        when(prefsRepo.findByUserId(user)).thenReturn(Optional.of(skipsSales));
    }

    @Test
    void keywordSearchPassesHerSkipListToTheSearch() {
        // Bug: the service never reads her preferences, so the search runs unfiltered.
        JobSearchPort port = mock(JobSearchPort.class);
        JobSearchQuery query = new JobSearchQuery("java", null, 0, 20, "postedAt", user);
        JobSearchResult filtered = new JobSearchResult(List.of(), 0, 0, 20, null);
        when(port.search(query, List.of("SALES"))).thenReturn(filtered);

        assertThat(new SearchJobsService(port, prefsRepo).searchJobs(query)).isSameAs(filtered);
    }

    @Test
    void semanticSearchLeavesOutASkippedCategory() {
        // Bug: semantic search returns the nearest postings whatever their category, so a
        // skipped category reaches her through "search by meaning".
        Job sales = Job.builder().id(UUID.randomUUID()).title("Sales").isActive(true)
                .jobCategory(JobCategory.SALES).build();
        Job software = Job.builder().id(UUID.randomUUID()).title("Java").isActive(true)
                .jobCategory(JobCategory.SOFTWARE_IT).build();
        AiProviderPort ai = mock(AiProviderPort.class);
        when(ai.embed(anyString())).thenReturn(new float[]{1f});
        JobEmbeddingRepositoryPort embeddings = mock(JobEmbeddingRepositoryPort.class);
        when(embeddings.findNearestNeighborJobIds(any(), anyInt())).thenReturn(List.of(sales.id(), software.id()));
        JobRepositoryPort jobRepo = mock(JobRepositoryPort.class);
        when(jobRepo.findByIds(any())).thenReturn(List.of(sales, software));

        List<Job> found = new SemanticJobSearchService(ai, embeddings, jobRepo, prefsRepo)
                .semanticSearch(user, "java", 10);

        assertThat(found).extracting(Job::id).containsExactly(software.id());
    }
}
