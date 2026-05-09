package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.ProfileSkillEntity;
import com.autoapplicant.adapter.persistence.repository.ProfileSkillJpaRepository;
import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.port.out.skills.ProfileSkillRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ProfileSkillPersistenceAdapter implements ProfileSkillRepositoryPort {

    private final ProfileSkillJpaRepository repo;

    public ProfileSkillPersistenceAdapter(ProfileSkillJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public List<ProfileSkill> findByUserId(UUID userId) {
        return repo.findByUserIdOrderByDisplayOrderAscSkillNameAsc(userId).stream()
                .map(this::toDomain).toList();
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
                e.getProficiencyLevel(), e.getYearsExperience(), e.isUsedInProduction(), e.getDisplayOrder());
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
        return e;
    }
}
