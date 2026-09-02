package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.ApplicationStatusEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApplicationStatusEventJpaRepository extends JpaRepository<ApplicationStatusEventEntity, UUID> {
    List<ApplicationStatusEventEntity> findByUserIdOrderByOccurredAtAsc(UUID userId);

    List<ApplicationStatusEventEntity> findByApplicationIdAndUserIdOrderByOccurredAtAsc(UUID applicationId, UUID userId);
}
