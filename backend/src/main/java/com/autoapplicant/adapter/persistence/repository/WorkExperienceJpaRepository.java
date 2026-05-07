package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.WorkExperienceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkExperienceJpaRepository extends JpaRepository<WorkExperienceEntity, UUID> {
    List<WorkExperienceEntity> findByUserIdOrderByDisplayOrderAsc(UUID userId);
    void deleteByIdAndUserId(UUID id, UUID userId);
}
