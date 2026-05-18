package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.WorkExperienceSkillEntity;
import com.autoapplicant.adapter.persistence.mapper.ProfileSectionMapper;
import com.autoapplicant.adapter.persistence.repository.WorkExperienceJpaRepository;
import com.autoapplicant.adapter.persistence.repository.WorkExperienceSkillJpaRepository;
import com.autoapplicant.domain.skill.SkillTaxonomy;
import com.autoapplicant.domain.user.WorkExperience;
import com.autoapplicant.port.out.skills.SkillTaxonomyRepositoryPort;
import com.autoapplicant.port.out.user.WorkExperienceRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class WorkExperiencePersistenceAdapter implements WorkExperienceRepositoryPort {

    private final WorkExperienceJpaRepository repo;
    private final WorkExperienceSkillJpaRepository skillRepo;
    private final SkillTaxonomyRepositoryPort taxonomyRepo;

    public WorkExperiencePersistenceAdapter(WorkExperienceJpaRepository repo,
                                             WorkExperienceSkillJpaRepository skillRepo,
                                             SkillTaxonomyRepositoryPort taxonomyRepo) {
        this.repo = repo;
        this.skillRepo = skillRepo;
        this.taxonomyRepo = taxonomyRepo;
    }

    @Override
    @Transactional
    public WorkExperience save(WorkExperience exp) {
        var saved = repo.save(ProfileSectionMapper.toEntity(exp));
        skillRepo.deleteByWorkExperienceId(saved.getId());
        List<SkillTaxonomy> skills = exp.skills() != null ? exp.skills() : List.of();
        for (SkillTaxonomy skill : skills) {
            if (skill.id() != null) {
                var link = new WorkExperienceSkillEntity();
                link.setWorkExperienceId(saved.getId());
                link.setTaxonomyId(skill.id());
                skillRepo.save(link);
            }
        }
        return ProfileSectionMapper.toDomain(saved, skills);
    }

    @Override
    public List<WorkExperience> findByUserId(UUID userId) {
        return loadWithSkills(repo.findByUserIdOrderByDisplayOrderAsc(userId));
    }

    @Override
    public Optional<WorkExperience> findById(UUID id) {
        return repo.findById(id).map(e -> loadWithSkills(List.of(e)).get(0));
    }

    @Override
    @Transactional
    public void deleteByIdAndUserId(UUID id, UUID userId) {
        repo.deleteByIdAndUserId(id, userId);
    }

    private List<WorkExperience> loadWithSkills(
            List<com.autoapplicant.adapter.persistence.entity.WorkExperienceEntity> entities) {
        if (entities.isEmpty()) return List.of();
        Set<UUID> ids = entities.stream()
                .map(com.autoapplicant.adapter.persistence.entity.WorkExperienceEntity::getId)
                .collect(Collectors.toSet());
        List<WorkExperienceSkillEntity> links = skillRepo.findByWorkExperienceIdIn(ids);
        Set<UUID> taxIds = links.stream()
                .map(WorkExperienceSkillEntity::getTaxonomyId)
                .collect(Collectors.toSet());
        Map<UUID, SkillTaxonomy> taxById = taxonomyRepo.findByIds(taxIds).stream()
                .collect(Collectors.toMap(SkillTaxonomy::id, s -> s));
        Map<UUID, List<SkillTaxonomy>> skillsByWeId = links.stream()
                .collect(Collectors.groupingBy(
                        WorkExperienceSkillEntity::getWorkExperienceId,
                        Collectors.mapping(
                                l -> taxById.get(l.getTaxonomyId()),
                                Collectors.filtering(Objects::nonNull, Collectors.toList()))));
        return entities.stream()
                .map(e -> ProfileSectionMapper.toDomain(e,
                        skillsByWeId.getOrDefault(e.getId(), List.of())))
                .toList();
    }
}
