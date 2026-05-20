package com.autoapplicant.adapter.persistence.mapper;

import com.autoapplicant.adapter.persistence.entity.RecommendationFeedbackEntity;
import com.autoapplicant.domain.matching.FeedbackType;
import com.autoapplicant.domain.matching.RecommendationFeedback;

public final class FeedbackMapper {

    private FeedbackMapper() {}

    public static RecommendationFeedback toDomain(RecommendationFeedbackEntity e) {
        return new RecommendationFeedback(e.getId(), e.getUserId(), e.getJobId(),
                FeedbackType.valueOf(e.getFeedbackType()), e.getCreatedAt());
    }

    public static RecommendationFeedbackEntity toEntity(RecommendationFeedback d) {
        RecommendationFeedbackEntity e = new RecommendationFeedbackEntity();
        e.setId(d.id());
        e.setUserId(d.userId());
        e.setJobId(d.jobId());
        e.setFeedbackType(d.feedbackType().name());
        return e;
    }
}
