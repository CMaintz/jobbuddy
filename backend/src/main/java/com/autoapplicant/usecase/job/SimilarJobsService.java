package com.autoapplicant.usecase.job;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.JobEmbedding;
import com.autoapplicant.port.in.job.GetSimilarJobsUseCase;
import com.autoapplicant.port.out.job.JobEmbeddingRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** "Similar roles": nearest neighbors of a job's own embedding, active jobs only. */
@Service
public class SimilarJobsService implements GetSimilarJobsUseCase {

    private final JobEmbeddingRepositoryPort embeddingRepo;
    private final JobRepositoryPort jobRepo;

    public SimilarJobsService(JobEmbeddingRepositoryPort embeddingRepo, JobRepositoryPort jobRepo) {
        this.embeddingRepo = embeddingRepo;
        this.jobRepo = jobRepo;
    }

    @Override
    public List<Job> getSimilarJobs(UUID jobId, int limit) {
        float[] vector = embeddingRepo.findByJobId(jobId)
                .map(JobEmbedding::embedding)
                .orElse(null);
        if (vector == null) return List.of();

        // Over-fetch: the job itself is its own nearest neighbor, and some hits may be inactive.
        List<UUID> nearestIds = embeddingRepo.findNearestNeighborJobIds(vector, limit * 2 + 1).stream()
                .filter(id -> !id.equals(jobId))
                .toList();

        Map<UUID, Job> byId = jobRepo.findByIds(nearestIds).stream()
                .collect(Collectors.toMap(Job::id, Function.identity()));

        // Preserve similarity order from the vector search
        return nearestIds.stream()
                .map(byId::get)
                .filter(job -> job != null && job.isActive())
                .limit(limit)
                .toList();
    }
}
