package com.autoapplicant.port.in.job;

import com.autoapplicant.domain.job.Job;

import java.util.List;

public interface SemanticSearchJobsUseCase {
    /** Embeds the free-text query and returns the semantically closest active jobs. */
    List<Job> semanticSearch(String query, int limit);
}
