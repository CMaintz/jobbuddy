package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.ProjectSkillEntity;
import com.autoapplicant.adapter.persistence.entity.ProjectSkillId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProjectSkillJpaRepository
        extends JpaRepository<ProjectSkillEntity, ProjectSkillId> {

    List<ProjectSkillEntity> findByProjectId(UUID projectId);

    List<ProjectSkillEntity> findByProjectIdIn(Collection<UUID> projectIds);

    @Modifying
    @Transactional
    void deleteByProjectId(UUID projectId);
}
