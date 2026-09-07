package com.autoapplicant.port.in.job;

import com.autoapplicant.domain.job.IgnoredJob;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface GetIgnoredJobsUseCase {
    List<IgnoredJob> getIgnoredJobs(UUID userId);
    Set<UUID> getIgnoredJobIds(UUID userId);
    void unignoreJob(UUID userId, UUID jobId);
}
