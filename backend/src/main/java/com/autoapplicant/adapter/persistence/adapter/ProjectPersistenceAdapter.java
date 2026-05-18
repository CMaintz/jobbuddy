package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.ProjectEntity;
import com.autoapplicant.adapter.persistence.entity.ProjectSkillEntity;
import com.autoapplicant.adapter.persistence.mapper.ProfileSectionMapper;
import com.autoapplicant.adapter.persistence.repository.ProjectJpaRepository;
import com.autoapplicant.adapter.persistence.repository.ProjectSkillJpaRepository;
import com.autoapplicant.domain.skill.SkillTaxonomy;
import com.autoapplicant.domain.user.Project;
import com.autoapplicant.port.out.skills.SkillTaxonomyRepositoryPort;
import com.autoapplicant.port.out.user.ProjectRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class ProjectPersistenceAdapter implements ProjectRepositoryPort {

    private final ProjectJpaRepository repo;
    private final ProjectSkillJpaRepository skillRepo;
    private final SkillTaxonomyRepositoryPort taxonomyRepo;

    public ProjectPersistenceAdapter(ProjectJpaRepository repo,
                                      ProjectSkillJpaRepository skillRepo,
                                      SkillTaxonomyRepositoryPort taxonomyRepo) {
        this.repo = repo;
        this.skillRepo = skillRepo;
        this.taxonomyRepo = taxonomyRepo;
    }

    @Override
    @Transactional
    public Project save(Project project) {
        var saved = repo.save(ProfileSectionMapper.toEntity(project));
        skillRepo.deleteByProjectId(saved.getId());
        List<SkillTaxonomy> skills = project.skills() != null ? project.skills() : List.of();
        for (SkillTaxonomy skill : skills) {
            if (skill.id() != null) {
                var link = new ProjectSkillEntity();
                link.setProjectId(saved.getId());
                link.setTaxonomyId(skill.id());
                skillRepo.save(link);
            }
        }
        return ProfileSectionMapper.toDomain(saved, skills);
    }

    @Override
    public List<Project> findByUserId(UUID userId) {
        return loadWithSkills(repo.findByUserIdOrderByDisplayOrderAsc(userId));
    }

    @Override
    public Optional<Project> findById(UUID id) {
        return repo.findById(id).map(e -> loadWithSkills(List.of(e)).get(0));
    }

    @Override
    @Transactional
    public void deleteByIdAndUserId(UUID id, UUID userId) {
        repo.deleteByIdAndUserId(id, userId);
    }

    private List<Project> loadWithSkills(List<ProjectEntity> entities) {
        if (entities.isEmpty()) return List.of();
        Set<UUID> ids = entities.stream().map(ProjectEntity::getId).collect(Collectors.toSet());
        List<ProjectSkillEntity> links = skillRepo.findByProjectIdIn(ids);
        Set<UUID> taxIds = links.stream()
                .map(ProjectSkillEntity::getTaxonomyId)
                .collect(Collectors.toSet());
        Map<UUID, SkillTaxonomy> taxById = taxonomyRepo.findByIds(taxIds).stream()
                .collect(Collectors.toMap(SkillTaxonomy::id, s -> s));
        Map<UUID, List<SkillTaxonomy>> skillsByProjectId = links.stream()
                .collect(Collectors.groupingBy(
                        ProjectSkillEntity::getProjectId,
                        Collectors.mapping(
                                l -> taxById.get(l.getTaxonomyId()),
                                Collectors.filtering(Objects::nonNull, Collectors.toList()))));
        return entities.stream()
                .map(e -> ProfileSectionMapper.toDomain(e,
                        skillsByProjectId.getOrDefault(e.getId(), List.of())))
                .toList();
    }
}
