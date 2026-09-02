package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.ResponseMetricEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ResponseMetricJpaRepository extends JpaRepository<ResponseMetricEntity, UUID> {
    List<ResponseMetricEntity> findByApplicationIdAndUserIdOrderByEventAtAsc(UUID applicationId, UUID userId);
}
