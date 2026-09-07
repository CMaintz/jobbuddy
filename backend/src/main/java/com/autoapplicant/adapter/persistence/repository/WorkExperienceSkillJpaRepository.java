package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.WorkExperienceSkillEntity;
import com.autoapplicant.adapter.persistence.entity.WorkExperienceSkillId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface WorkExperienceSkillJpaRepository
        extends JpaRepository<WorkExperienceSkillEntity, WorkExperienceSkillId> {

    List<WorkExperienceSkillEntity> findByWorkExperienceId(UUID workExperienceId);

    List<WorkExperienceSkillEntity> findByWorkExperienceIdIn(Collection<UUID> workExperienceIds);

    @Modifying
    @Transactional
    void deleteByWorkExperienceId(UUID workExperienceId);
}
