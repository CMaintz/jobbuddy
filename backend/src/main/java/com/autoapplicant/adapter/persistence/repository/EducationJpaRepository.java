package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.EducationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EducationJpaRepository extends JpaRepository<EducationEntity, UUID> {
    List<EducationEntity> findByUserIdOrderByDisplayOrderAsc(UUID userId);
    void deleteByIdAndUserId(UUID id, UUID userId);
}
