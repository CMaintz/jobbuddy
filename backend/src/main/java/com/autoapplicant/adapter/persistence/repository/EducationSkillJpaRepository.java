package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.EducationSkillEntity;
import com.autoapplicant.adapter.persistence.entity.EducationSkillId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface EducationSkillJpaRepository
        extends JpaRepository<EducationSkillEntity, EducationSkillId> {

    List<EducationSkillEntity> findByEducationId(UUID educationId);

    List<EducationSkillEntity> findByEducationIdIn(Collection<UUID> educationIds);

    @Modifying
    @Transactional
    void deleteByEducationId(UUID educationId);
}
