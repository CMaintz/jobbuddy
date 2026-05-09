package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.IgnoredJobEntity;
import com.autoapplicant.adapter.persistence.repository.IgnoredJobJpaRepository;
import com.autoapplicant.domain.job.IgnoredJob;
import com.autoapplicant.port.out.job.IgnoredJobRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class IgnoredJobPersistenceAdapter implements IgnoredJobRepositoryPort {

    private final IgnoredJobJpaRepository repo;

    public IgnoredJobPersistenceAdapter(IgnoredJobJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public IgnoredJob save(IgnoredJob ignoredJob) {
        if (repo.existsByUserIdAndJobId(ignoredJob.userId(), ignoredJob.jobId())) {
            return ignoredJob;
        }
        IgnoredJobEntity e = new IgnoredJobEntity();
        e.setUserId(ignoredJob.userId());
        e.setJobId(ignoredJob.jobId());
        e.setReason(ignoredJob.reason());
        IgnoredJobEntity saved = repo.save(e);
        return new IgnoredJob(saved.getId(), saved.getUserId(), saved.getJobId(),
                saved.getReason(), saved.getIgnoredAt());
    }

    @Override
    public List<IgnoredJob> findByUserIdOrderByIgnoredAtDesc(UUID userId) {
        return repo.findByUserIdOrderByIgnoredAtDesc(userId).stream()
                .map(e -> new IgnoredJob(e.getId(), e.getUserId(), e.getJobId(),
                        e.getReason(), e.getIgnoredAt()))
                .toList();
    }

    @Override
    public Set<UUID> findJobIdsByUserId(UUID userId) {
        return new HashSet<>(repo.findJobIdsByUserId(userId));
    }

    @Override
    public boolean isIgnored(UUID userId, UUID jobId) {
        return repo.existsByUserIdAndJobId(userId, jobId);
    }

    @Override
    @Transactional
    public void deleteByUserIdAndJobId(UUID userId, UUID jobId) {
        repo.deleteByUserIdAndJobId(userId, jobId);
    }
}
