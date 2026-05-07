package com.autoapplicant.domain.matching;

import java.time.Instant;
import java.util.UUID;

public record RecommendationFeedback(
        UUID id,
        UUID userId,
        UUID jobId,
        FeedbackType feedbackType,
        Instant createdAt
) {}
