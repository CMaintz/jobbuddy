package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.InterviewQuestionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InterviewQuestionJpaRepository extends JpaRepository<InterviewQuestionEntity, UUID> {
    List<InterviewQuestionEntity> findByJobIdAndUserIdOrderByDisplayOrderAsc(UUID jobId, UUID userId);
    Optional<InterviewQuestionEntity> findByIdAndUserId(UUID id, UUID userId);
    void deleteByIdAndUserId(UUID id, UUID userId);
}
