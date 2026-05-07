package com.autoapplicant.usecase.matching;

import com.autoapplicant.domain.matching.FeedbackType;
import com.autoapplicant.domain.matching.RecommendationFeedback;
import com.autoapplicant.port.in.matching.SubmitRecommendationFeedbackUseCase;
import com.autoapplicant.port.out.matching.RecommendationFeedbackRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class RecommendationFeedbackService implements SubmitRecommendationFeedbackUseCase {

    private final RecommendationFeedbackRepositoryPort feedbackRepo;

    public RecommendationFeedbackService(RecommendationFeedbackRepositoryPort feedbackRepo) {
        this.feedbackRepo = feedbackRepo;
    }

    @Override
    public RecommendationFeedback submitFeedback(UUID userId, UUID jobId, FeedbackType feedbackType) {
        // Upsert: replace any existing feedback for this user+job pair
        feedbackRepo.findByUserIdAndJobId(userId, jobId)
                .ifPresent(existing -> feedbackRepo.deleteByUserIdAndJobId(userId, jobId));
        return feedbackRepo.save(new RecommendationFeedback(null, userId, jobId, feedbackType, null));
    }

    @Override
    public void removeFeedback(UUID userId, UUID jobId) {
        feedbackRepo.deleteByUserIdAndJobId(userId, jobId);
    }
}
