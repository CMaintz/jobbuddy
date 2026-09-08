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
        // No taxonomy join: every write path resolves the category and stores it on the row, so
        // reading it back is a column read rather than a second query.
        return repo.findByUserIdOrderByDisplayOrderAscSkillNameAsc(userId).stream()
                .map(this::toDomain)
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
        return new ProfileSkill(e.getId(), e.getUserId(), e.getSkillName(), e.getTaxonomyId(),
                e.getProficiencyLevel(), e.getYearsExperience(), e.isUsedInProduction(),
                e.getDisplayOrder(), e.getCategory());
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
