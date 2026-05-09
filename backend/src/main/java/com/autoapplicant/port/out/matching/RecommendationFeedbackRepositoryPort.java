package com.autoapplicant.port.out.matching;

import com.autoapplicant.domain.matching.RecommendationFeedback;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecommendationFeedbackRepositoryPort {
    RecommendationFeedback save(RecommendationFeedback feedback);
    Optional<RecommendationFeedback> findByUserIdAndJobId(UUID userId, UUID jobId);
    List<RecommendationFeedback> findByUserId(UUID userId);
    void deleteByUserIdAndJobId(UUID userId, UUID jobId);
}
