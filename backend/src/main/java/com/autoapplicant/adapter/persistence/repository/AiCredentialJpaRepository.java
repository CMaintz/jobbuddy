package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.AiCredentialEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AiCredentialJpaRepository extends JpaRepository<AiCredentialEntity, UUID> {
}
