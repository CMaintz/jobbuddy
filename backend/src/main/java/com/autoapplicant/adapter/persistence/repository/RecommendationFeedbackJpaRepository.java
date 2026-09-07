package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.RecommendationFeedbackEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecommendationFeedbackJpaRepository extends JpaRepository<RecommendationFeedbackEntity, UUID> {
    Optional<RecommendationFeedbackEntity> findByUserIdAndJobId(UUID userId, UUID jobId);
    List<RecommendationFeedbackEntity> findByUserId(UUID userId);
    void deleteByUserIdAndJobId(UUID userId, UUID jobId);
}
