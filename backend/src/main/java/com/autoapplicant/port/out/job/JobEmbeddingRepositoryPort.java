package com.autoapplicant.port.out.job;

import com.autoapplicant.domain.job.JobEmbedding;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobEmbeddingRepositoryPort {
    JobEmbedding save(JobEmbedding embedding);
    Optional<JobEmbedding> findByJobId(UUID jobId);
    List<UUID> findNearestNeighborJobIds(float[] vector, int limit);
}
