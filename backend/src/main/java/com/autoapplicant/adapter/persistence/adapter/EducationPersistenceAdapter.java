package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.EducationEntity;
import com.autoapplicant.adapter.persistence.entity.EducationSkillEntity;
import com.autoapplicant.adapter.persistence.mapper.ProfileSectionMapper;
import com.autoapplicant.adapter.persistence.repository.EducationJpaRepository;
import com.autoapplicant.adapter.persistence.repository.EducationSkillJpaRepository;
import com.autoapplicant.domain.skill.SkillTaxonomy;
import com.autoapplicant.domain.user.Education;
import com.autoapplicant.port.out.skills.SkillTaxonomyRepositoryPort;
import com.autoapplicant.port.out.user.EducationRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class EducationPersistenceAdapter implements EducationRepositoryPort {

    private final EducationJpaRepository repo;
    private final EducationSkillJpaRepository skillRepo;
    private final SkillTaxonomyRepositoryPort taxonomyRepo;

    public EducationPersistenceAdapter(EducationJpaRepository repo,
                                        EducationSkillJpaRepository skillRepo,
                                        SkillTaxonomyRepositoryPort taxonomyRepo) {
        this.repo = repo;
        this.skillRepo = skillRepo;
        this.taxonomyRepo = taxonomyRepo;
    }

    @Override
    @Transactional
    public Education save(Education education) {
        var saved = repo.save(ProfileSectionMapper.toEntity(education));
        skillRepo.deleteByEducationId(saved.getId());
        List<SkillTaxonomy> skills = education.skills() != null ? education.skills() : List.of();
        for (SkillTaxonomy skill : skills) {
            if (skill.id() != null) {
                var link = new EducationSkillEntity();
                link.setEducationId(saved.getId());
                link.setTaxonomyId(skill.id());
                skillRepo.save(link);
            }
        }
        return ProfileSectionMapper.toDomain(saved, skills);
    }

    @Override
    public List<Education> findByUserId(UUID userId) {
        return loadWithSkills(repo.findByUserIdOrderByDisplayOrderAsc(userId));
    }

    @Override
    public Optional<Education> findById(UUID id) {
        return repo.findById(id).map(e -> loadWithSkills(List.of(e)).get(0));
    }

    @Override
    @Transactional
    public void deleteByIdAndUserId(UUID id, UUID userId) {
        repo.deleteByIdAndUserId(id, userId);
    }

    private List<Education> loadWithSkills(List<EducationEntity> entities) {
        if (entities.isEmpty()) return List.of();
        Set<UUID> ids = entities.stream().map(EducationEntity::getId).collect(Collectors.toSet());
        List<EducationSkillEntity> links = skillRepo.findByEducationIdIn(ids);
        Set<UUID> taxIds = links.stream()
                .map(EducationSkillEntity::getTaxonomyId)
                .collect(Collectors.toSet());
        Map<UUID, SkillTaxonomy> taxById = taxonomyRepo.findByIds(taxIds).stream()
                .collect(Collectors.toMap(SkillTaxonomy::id, s -> s));
        Map<UUID, List<SkillTaxonomy>> skillsByEduId = links.stream()
                .collect(Collectors.groupingBy(
                        EducationSkillEntity::getEducationId,
                        Collectors.mapping(
                                l -> taxById.get(l.getTaxonomyId()),
                                Collectors.filtering(Objects::nonNull, Collectors.toList()))));
        return entities.stream()
                .map(e -> ProfileSectionMapper.toDomain(e,
                        skillsByEduId.getOrDefault(e.getId(), List.of())))
                .toList();
    }
}
