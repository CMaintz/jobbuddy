package com.autoapplicant.usecase.job;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.port.in.job.SemanticSearchJobsUseCase;
import com.autoapplicant.port.out.ai.AiProviderPort;
import com.autoapplicant.port.out.job.JobEmbeddingRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * "Search by meaning": embeds the query and ranks jobs by vector distance,
 * complementing the keyword search (Typesense) which misses paraphrases.
 */
@Service
public class SemanticJobSearchService implements SemanticSearchJobsUseCase {

    private static final Logger log = LoggerFactory.getLogger(SemanticJobSearchService.class);

    private final AiProviderPort aiProvider;
    private final JobEmbeddingRepositoryPort embeddingRepo;
    private final JobRepositoryPort jobRepo;

    public SemanticJobSearchService(@Qualifier("enrichmentAiProvider") AiProviderPort aiProvider,
                                    JobEmbeddingRepositoryPort embeddingRepo,
                                    JobRepositoryPort jobRepo) {
        this.aiProvider = aiProvider;
        this.embeddingRepo = embeddingRepo;
        this.jobRepo = jobRepo;
    }

    @Override
    public List<Job> semanticSearch(String query, int limit) {
        if (query == null || query.isBlank()) return List.of();
        try {
            float[] vector = aiProvider.embed(query.trim());
            // Over-fetch to survive inactive hits without a second query
            List<UUID> nearestIds = embeddingRepo.findNearestNeighborJobIds(vector, limit * 2);

            Map<UUID, Job> byId = jobRepo.findByIds(nearestIds).stream()
                    .collect(Collectors.toMap(Job::id, Function.identity()));

            // Preserve similarity order from the vector search
            return nearestIds.stream()
                    .map(byId::get)
                    .filter(job -> job != null && job.isActive())
                    .limit(limit)
                    .toList();
        } catch (Exception e) {
            log.warn("Semantic search failed for '{}': {}", query, e.getMessage());
            return List.of();
        }
    }
}
