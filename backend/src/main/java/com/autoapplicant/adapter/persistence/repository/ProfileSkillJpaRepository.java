package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.ProfileSkillEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProfileSkillJpaRepository extends JpaRepository<ProfileSkillEntity, UUID> {
    List<ProfileSkillEntity> findByUserIdOrderByDisplayOrderAscSkillNameAsc(UUID userId);
}
