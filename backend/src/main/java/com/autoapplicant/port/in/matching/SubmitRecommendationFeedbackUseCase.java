package com.autoapplicant.port.in.matching;

import com.autoapplicant.domain.matching.FeedbackType;
import com.autoapplicant.domain.matching.RecommendationFeedback;

import java.util.UUID;

public interface SubmitRecommendationFeedbackUseCase {
    RecommendationFeedback submitFeedback(UUID userId, UUID jobId, FeedbackType feedbackType);
    void removeFeedback(UUID userId, UUID jobId);
}
