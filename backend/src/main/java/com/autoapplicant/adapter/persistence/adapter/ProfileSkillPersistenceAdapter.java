package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.ProfileSkillEntity;
import com.autoapplicant.adapter.persistence.repository.ProfileSkillJpaRepository;
import com.autoapplicant.adapter.persistence.repository.SkillTaxonomyJpaRepository;
import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.port.out.skills.ProfileSkillRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class ProfileSkillPersistenceAdapter implements ProfileSkillRepositoryPort {

    private final ProfileSkillJpaRepository repo;
    private final SkillTaxonomyJpaRepository taxonomyRepo;

    public ProfileSkillPersistenceAdapter(ProfileSkillJpaRepository repo,
                                           SkillTaxonomyJpaRepository taxonomyRepo) {
        this.repo = repo;
        this.taxonomyRepo = taxonomyRepo;
    }

    @Override
    public List<ProfileSkill> findByUserId(UUID userId) {
        List<ProfileSkillEntity> entities = repo.findByUserIdOrderByDisplayOrderAscSkillNameAsc(userId);

        // Batch-load taxonomy categories for skills that have a taxonomyId
        List<UUID> taxonomyIds = entities.stream()
                .map(ProfileSkillEntity::getTaxonomyId)
                .filter(id -> id != null)
                .distinct().toList();
        Map<UUID, String> categoryById = taxonomyIds.isEmpty() ? Map.of()
                : taxonomyRepo.findByIdIn(taxonomyIds).stream()
                    .collect(Collectors.toMap(t -> t.getId(), t -> t.getCategory() != null ? t.getCategory() : "Custom"));

        return entities.stream()
                .map(e -> toDomainWithCategory(e, categoryById.get(e.getTaxonomyId())))
                .toList();
    }

    @Override
    public Optional<ProfileSkill> findById(UUID id) {
        return repo.findById(id).map(this::toDomain);
    }

    @Override
    public ProfileSkill save(ProfileSkill skill) {
        ProfileSkillEntity e = toEntity(skill);
        return toDomain(repo.save(e));
    }

    @Override
    public void deleteById(UUID id) {
        repo.deleteById(id);
    }

    private ProfileSkill toDomain(ProfileSkillEntity e) {
        return toDomainWithCategory(e, null);
    }

    /**
     * The row's own category wins; {@code taxonomyCategory} is the fallback for rows written
     * before V070 gave profile_skills a category of its own. Passing null for it is therefore
     * safe on the save path — a saved row already carries whatever category was resolved.
     */
    private ProfileSkill toDomainWithCategory(ProfileSkillEntity e, String taxonomyCategory) {
        String category = e.getCategory() != null ? e.getCategory() : taxonomyCategory;
        return new ProfileSkill(e.getId(), e.getUserId(), e.getSkillName(), e.getTaxonomyId(),
                e.getProficiencyLevel(), e.getYearsExperience(), e.isUsedInProduction(),
                e.getDisplayOrder(), category);
    }

    private ProfileSkillEntity toEntity(ProfileSkill s) {
        ProfileSkillEntity e = new ProfileSkillEntity();
        e.setId(s.id());
        e.setUserId(s.userId());
        e.setSkillName(s.skillName());
        e.setTaxonomyId(s.taxonomyId());
        e.setProficiencyLevel(s.proficiencyLevel());
        e.setYearsExperience(s.yearsExperience());
        e.setUsedInProduction(s.usedInProduction());
        e.setDisplayOrder(s.displayOrder());
        e.setCategory(s.category());
        return e;
    }
}
