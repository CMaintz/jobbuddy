package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.mapper.JobMapper;
import com.autoapplicant.adapter.persistence.repository.JobJpaRepository;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class JobPersistenceAdapter implements JobRepositoryPort {

    private final JobJpaRepository repo;

    public JobPersistenceAdapter(JobJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public Job save(Job job) {
        var entity = JobMapper.toEntity(job);
        // URL-check state and the content fingerprint live only on the entity; carry them
        // over so crawl/enrichment re-saves don't reset them. The duplicate group is on the
        // domain but only the dedup pass sets it — preserve an existing group when the
        // incoming domain object doesn't carry one.
        if (job.id() != null) {
            repo.findById(job.id()).ifPresent(existing -> {
                entity.setLastUrlCheckAt(existing.getLastUrlCheckAt());
                entity.setUrlCheckFailures(existing.getUrlCheckFailures());
                entity.setContentFingerprint(existing.getContentFingerprint());
                if (entity.getDuplicateGroupId() == null && existing.getDuplicateGroupId() != null) {
                    entity.setDuplicateGroupId(existing.getDuplicateGroupId());
                }
            });
        }
        return JobMapper.toDomain(repo.save(entity));
    }

    @Override
    public List<Job> saveAll(List<Job> jobs) {
        return repo.saveAll(jobs.stream().map(JobMapper::toEntity).collect(Collectors.toList()))
                .stream().map(JobMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public Optional<Job> findById(UUID id) {
        return repo.findById(id).map(JobMapper::toDomain);
    }

    @Override
    public List<Job> findAll(int page, int size) {
        return repo.findAll(PageRequest.of(page, size)).stream()
                .map(JobMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Job> findActive(int page, int size) {
        return repo.findActiveJobs(PageRequest.of(page, size)).stream()
                .map(JobMapper::toDomain).toList();
    }

    @Override
    public List<Job> findAllExcluding(Set<UUID> excludedIds, int page, int size) {
        if (excludedIds == null || excludedIds.isEmpty()) {
            return findActive(page, size);
        }
        return repo.findAllExcluding(excludedIds, PageRequest.of(page, size))
                .stream().map(JobMapper::toDomain).toList();
    }

    @Override
    public Optional<Job> findBySourceAndSourceJobId(JobSource source, String sourceJobId) {
        return repo.findBySourceAndSourceJobId(source.name(), sourceJobId).map(JobMapper::toDomain);
    }

    @Override
    public boolean existsBySourceAndSourceJobId(JobSource source, String sourceJobId) {
        return repo.existsBySourceAndSourceJobId(source.name(), sourceJobId);
    }

    @Override
    public Optional<Job> findByUrl(String url) {
        return repo.findByUrl(url).map(JobMapper::toDomain);
    }

    @Override
    public List<Job> findByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return repo.findAllById(ids).stream().map(JobMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<UUID> findStaleActiveJobIds(Instant cutoff) {
        return repo.findStaleActiveJobIds(cutoff);
    }

    @Override
    public int deactivateStaleJobs(Instant cutoff) {
        return repo.deactivateStaleJobs(cutoff);
    }

    @Override
    public List<Job> findUrlCheckCandidates(Instant recheckCutoff, int limit) {
        return repo.findUrlCheckCandidates(recheckCutoff, PageRequest.of(0, limit))
                .stream().map(JobMapper::toDomain).toList();
    }

    @Override
    public List<UUID> deactivateDeadlineExpiredJobs(java.time.LocalDate before) {
        List<UUID> ids = repo.findActiveWithDeadlineBefore(before);
        if (!ids.isEmpty()) repo.deactivateDeadlineExpired(before);
        return ids;
    }

    @Override
    public void markUrlAlive(UUID jobId) {
        repo.findById(jobId).ifPresent(e -> {
            e.setUrlCheckFailures(0);
            e.setLastUrlCheckAt(Instant.now());
            e.setLastSeenAt(Instant.now()); // confirmed alive counts as "seen" for staleness expiry
            repo.save(e);
        });
    }

    @Override
    public void markUrlCheckInconclusive(UUID jobId) {
        repo.findById(jobId).ifPresent(e -> {
            e.setLastUrlCheckAt(Instant.now());
            repo.save(e);
        });
    }

    @Override
    public boolean markUrlTakenDown(UUID jobId, int failureThreshold) {
        return repo.findById(jobId).map(e -> {
            e.setUrlCheckFailures(e.getUrlCheckFailures() + 1);
            e.setLastUrlCheckAt(Instant.now());
            boolean deactivate = e.getUrlCheckFailures() >= failureThreshold && e.isActive();
            if (deactivate) e.setActive(false);
            repo.save(e);
            return deactivate;
        }).orElse(false);
    }

    @Override
    public Optional<Job> findActiveDuplicateByFingerprint(long fingerprint, UUID excludeId) {
        return repo.findActiveByContentFingerprint(fingerprint, excludeId, PageRequest.of(0, 1))
                .stream().findFirst().map(JobMapper::toDomain);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void assignContentFingerprint(UUID jobId, long fingerprint) {
        repo.updateContentFingerprint(jobId, fingerprint);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void assignDuplicateGroup(UUID jobId, UUID duplicateGroupId) {
        repo.updateDuplicateGroup(jobId, duplicateGroupId);
    }

    @Override
    public List<Job> findUnenriched(int limit) {
        return repo.findUnenriched(org.springframework.data.domain.PageRequest.of(0, limit))
                .stream().map(JobMapper::toDomain).collect(java.util.stream.Collectors.toList());
    }

    @Override
    public long count() {
        return repo.count();
    }

    @Override
    public long countActive() {
        return repo.countByIsActiveTrue();
    }
}
