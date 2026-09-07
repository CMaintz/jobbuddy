package com.autoapplicant.port.out.job;

import com.autoapplicant.domain.job.IgnoredJob;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface IgnoredJobRepositoryPort {
    IgnoredJob save(IgnoredJob ignoredJob);
    List<IgnoredJob> findByUserIdOrderByIgnoredAtDesc(UUID userId);
    Set<UUID> findJobIdsByUserId(UUID userId);
    boolean isIgnored(UUID userId, UUID jobId);
    void deleteByUserIdAndJobId(UUID userId, UUID jobId);
}
