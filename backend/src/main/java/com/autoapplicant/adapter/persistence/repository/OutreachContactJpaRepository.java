package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.OutreachContactEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutreachContactJpaRepository extends JpaRepository<OutreachContactEntity, UUID> {
    List<OutreachContactEntity> findByUserId(UUID userId);
    Optional<OutreachContactEntity> findByUserIdAndCompanyId(UUID userId, UUID companyId);
}
