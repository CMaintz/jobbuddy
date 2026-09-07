package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.JobEmbeddingEntity;
import com.autoapplicant.adapter.persistence.repository.JobEmbeddingJpaRepository;
import com.autoapplicant.domain.job.JobEmbedding;
import com.autoapplicant.port.out.job.JobEmbeddingRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JobEmbeddingPersistenceAdapter implements JobEmbeddingRepositoryPort {

    private final JobEmbeddingJpaRepository repo;

    public JobEmbeddingPersistenceAdapter(JobEmbeddingJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public JobEmbedding save(JobEmbedding embedding) {
        JobEmbeddingEntity e = repo.findByJobId(embedding.jobId()).orElse(new JobEmbeddingEntity());
        e.setJobId(embedding.jobId());
        e.setEmbedding(embedding.embedding());
        e.setModel(embedding.model());
        JobEmbeddingEntity saved = repo.save(e);
        return new JobEmbedding(saved.getId(), saved.getJobId(), saved.getEmbedding(),
                saved.getModel(), saved.getCreatedAt());
    }

    @Override
    public Optional<JobEmbedding> findByJobId(UUID jobId) {
        return repo.findByJobId(jobId).map(e ->
                new JobEmbedding(e.getId(), e.getJobId(), e.getEmbedding(), e.getModel(), e.getCreatedAt()));
    }

    @Override
    public List<UUID> findNearestNeighborJobIds(float[] vector, int limit) {
        String pgVectorString = Arrays.toString(vector).replace('[', '[').replace(']', ']');
        return repo.findNearestNeighborJobIds(pgVectorString, limit);
    }
}
