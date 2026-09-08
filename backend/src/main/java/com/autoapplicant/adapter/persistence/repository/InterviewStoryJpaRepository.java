package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.InterviewStoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InterviewStoryJpaRepository extends JpaRepository<InterviewStoryEntity, UUID> {
    List<InterviewStoryEntity> findByUserIdOrderByUpdatedAtDesc(UUID userId);
    void deleteByIdAndUserId(UUID id, UUID userId);
}
