package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.CvVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CvVersionJpaRepository extends JpaRepository<CvVersionEntity, UUID> {
    List<CvVersionEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
