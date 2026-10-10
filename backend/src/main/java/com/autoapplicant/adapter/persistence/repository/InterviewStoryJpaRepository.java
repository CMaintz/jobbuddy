package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.InterviewStoryEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewStoryJpaRepository extends JpaRepository<InterviewStoryEntity, UUID> {
    List<InterviewStoryEntity> findByUserIdOrderByUpdatedAtDesc(UUID userId);
    Optional<InterviewStoryEntity> findByIdAndUserId(UUID id, UUID userId);
    void deleteByIdAndUserId(UUID id, UUID userId);
}
