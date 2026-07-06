package com.autoapplicant.port.out.job;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.JobSource;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface JobRepositoryPort {
    Job save(Job job);
    List<Job> saveAll(List<Job> jobs);
    Optional<Job> findById(UUID id);
    List<Job> findAll(int page, int size);
    List<Job> findAllExcluding(Set<UUID> excludedIds, int page, int size);
    Optional<Job> findBySourceAndSourceJobId(JobSource source, String sourceJobId);
    boolean existsBySourceAndSourceJobId(JobSource source, String sourceJobId);
    Optional<Job> findByUrl(String url);
    List<Job> findByIds(List<UUID> ids);
    List<Job> findUnenriched(int limit);
    int deactivateStaleJobs(java.time.Instant cutoff);
    long count();
}
