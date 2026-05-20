package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.mapper.FeedbackMapper;
import com.autoapplicant.adapter.persistence.repository.RecommendationFeedbackJpaRepository;
import com.autoapplicant.domain.matching.RecommendationFeedback;
import com.autoapplicant.port.out.matching.RecommendationFeedbackRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class RecommendationFeedbackPersistenceAdapter implements RecommendationFeedbackRepositoryPort {

    private final RecommendationFeedbackJpaRepository repo;

    public RecommendationFeedbackPersistenceAdapter(RecommendationFeedbackJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public RecommendationFeedback save(RecommendationFeedback feedback) {
        return FeedbackMapper.toDomain(repo.save(FeedbackMapper.toEntity(feedback)));
    }

    @Override
    public Optional<RecommendationFeedback> findByUserIdAndJobId(UUID userId, UUID jobId) {
        return repo.findByUserIdAndJobId(userId, jobId).map(FeedbackMapper::toDomain);
    }

    @Override
    public List<RecommendationFeedback> findByUserId(UUID userId) {
        return repo.findByUserId(userId).stream().map(FeedbackMapper::toDomain).toList();
    }

    @Override
    @Transactional
    public void deleteByUserIdAndJobId(UUID userId, UUID jobId) {
        repo.deleteByUserIdAndJobId(userId, jobId);
    }
}
