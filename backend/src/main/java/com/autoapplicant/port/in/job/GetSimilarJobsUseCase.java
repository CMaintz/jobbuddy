package com.autoapplicant.port.in.job;

import com.autoapplicant.domain.job.Job;

import java.util.List;
import java.util.UUID;

public interface GetSimilarJobsUseCase {
    /** Active jobs semantically closest to the given one (embedding nearest-neighbors). */
    List<Job> getSimilarJobs(UUID jobId, int limit);
}
