package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.JobEmbeddingEntity;
import com.autoapplicant.adapter.persistence.repository.JobEmbeddingJpaRepository;
import com.autoapplicant.domain.job.JobEmbedding;
import com.autoapplicant.port.in.ai.ResolveEmbeddingChoiceUseCase;
import com.autoapplicant.port.out.job.JobEmbeddingRepositoryPort;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Job vectors, one per job and model. Every read is scoped to the install's stored model, so no
 * caller can compare vectors across models: after a provider switch the old vectors stay (a
 * switch back finds them) but are invisible until then.
 */
@Component
public class JobEmbeddingPersistenceAdapter implements JobEmbeddingRepositoryPort {

    private final JobEmbeddingJpaRepository repo;
    private final ResolveEmbeddingChoiceUseCase activeChoice;

    public JobEmbeddingPersistenceAdapter(JobEmbeddingJpaRepository repo,
                                          ResolveEmbeddingChoiceUseCase activeChoice) {
        this.repo = repo;
        this.activeChoice = activeChoice;
    }

    @Override
    public JobEmbedding save(JobEmbedding embedding) {
        JobEmbeddingEntity e = repo.findByJobIdAndModel(embedding.jobId(), embedding.model())
                .orElse(new JobEmbeddingEntity());
        e.setJobId(embedding.jobId());
        e.setEmbedding(embedding.embedding());
        e.setModel(embedding.model());
        JobEmbeddingEntity saved = repo.save(e);
        return new JobEmbedding(saved.getId(), saved.getJobId(), saved.getEmbedding(),
                saved.getModel(), saved.getCreatedAt());
    }

    @Override
    public Optional<JobEmbedding> findByJobId(UUID jobId) {
        return repo.findByJobIdAndModel(jobId, activeModel()).map(e ->
                new JobEmbedding(e.getId(), e.getJobId(), e.getEmbedding(), e.getModel(), e.getCreatedAt()));
    }

    @Override
    public List<UUID> findNearestNeighborJobIds(float[] vector, int limit) {
        return repo.findNearestNeighborJobIds(Arrays.toString(vector), activeModel(), limit);
    }

    private String activeModel() {
        return activeChoice.currentChoice().model();
    }
}
