package com.autoapplicant.port.in.job;

import com.autoapplicant.domain.job.Job;

import java.util.List;
import java.util.UUID;

public interface GetSavedJobsUseCase {
    List<Job> getSavedJobs(UUID userId);
}
