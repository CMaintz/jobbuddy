package com.autoapplicant.usecase.matching;

import com.autoapplicant.domain.matching.MatchLabel;
import com.autoapplicant.domain.matching.MatchResult;
import com.autoapplicant.port.in.job.GetRecommendationsUseCase;
import com.autoapplicant.port.out.ai.AiProviderPort;
import com.autoapplicant.port.out.job.JobEmbeddingRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.user.ProfileRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MatchingService implements GetRecommendationsUseCase {

    private static final Logger log = LoggerFactory.getLogger(MatchingService.class);

    private final JobEmbeddingRepositoryPort embeddingRepo;
    private final JobRepositoryPort jobRepo;
    private final ProfileRepositoryPort profileRepo;
    private final AiProviderPort aiProvider;

    public MatchingService(JobEmbeddingRepositoryPort embeddingRepo, JobRepositoryPort jobRepo,
                           ProfileRepositoryPort profileRepo, AiProviderPort aiProvider) {
        this.embeddingRepo = embeddingRepo;
        this.jobRepo = jobRepo;
        this.profileRepo = profileRepo;
        this.aiProvider = aiProvider;
    }

    @Override
    public List<MatchResult> getRecommendations(UUID userId, int limit) {
        try {
            // Get user profile text and embed it
            String profileText = profileRepo.findByUserId(userId)
                    .map(p -> p.headline() + " " + p.summary() + " "
                            + String.join(" ", p.skills()) + " "
                            + String.join(" ", p.technologies()))
                    .orElse("");

            if (profileText.isBlank()) return List.of();

            float[] userEmbedding = aiProvider.embed(profileText);
            List<UUID> nearestJobIds = embeddingRepo.findNearestNeighborJobIds(userEmbedding, limit * 2);

            return nearestJobIds.stream()
                    .limit(limit)
                    .flatMap(jobId -> jobRepo.findById(jobId).stream())
                    .map(job -> {
                        int score = 75; // simplified score — real impl uses cosine similarity
                        return new MatchResult(job.id(), userId, job, true,
                                0.75, 0.0, score, MatchLabel.fromScore(score));
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Recommendation generation failed for user {}: {}", userId, e.getMessage());
            return List.of();
        }
    }
}
