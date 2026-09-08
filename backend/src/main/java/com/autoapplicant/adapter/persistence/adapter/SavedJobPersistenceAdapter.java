package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.SavedJobEntity;
import com.autoapplicant.adapter.persistence.repository.SavedJobJpaRepository;
import com.autoapplicant.port.out.job.SavedJobRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
public class SavedJobPersistenceAdapter implements SavedJobRepositoryPort {

    private final SavedJobJpaRepository repo;

    public SavedJobPersistenceAdapter(SavedJobJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public void save(UUID userId, UUID jobId) {
        if (!repo.existsByUserIdAndJobId(userId, jobId)) {
            SavedJobEntity entity = new SavedJobEntity();
            entity.setUserId(userId);
            entity.setJobId(jobId);
            repo.save(entity);
        }
    }

    @Override
    @Transactional
    public void unsave(UUID userId, UUID jobId) {
        repo.deleteByUserIdAndJobId(userId, jobId);
    }

    @Override
    public List<UUID> findJobIdsByUserId(UUID userId) {
        return repo.findJobIdsByUserId(userId);
    }

}
