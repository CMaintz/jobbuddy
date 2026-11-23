package com.autoapplicant.port.out.job;

import com.autoapplicant.domain.search.JobSearchQuery;
import com.autoapplicant.domain.search.JobSearchResult;

/**
 * Keyword search over postings. There is no index to maintain: the search vector is a
 * generated column on the row itself, so writing a job is what makes it findable.
 */
public interface JobSearchPort {
    default JobSearchResult search(JobSearchQuery query) {
        return search(query, java.util.List.of());
    }

    /** As {@link #search(JobSearchQuery)}, leaving out postings in {@code excludedCategories}. */
    JobSearchResult search(JobSearchQuery query, java.util.Collection<String> excludedCategories);
}
