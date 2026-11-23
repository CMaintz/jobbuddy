package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.ChatPlanEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatPlanJpaRepository extends JpaRepository<ChatPlanEntity, UUID> {}
