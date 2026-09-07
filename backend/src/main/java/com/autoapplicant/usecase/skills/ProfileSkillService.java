package com.autoapplicant.usecase.skills;

import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.port.in.skills.ManageProfileSkillsUseCase;
import com.autoapplicant.port.out.skills.ProfileSkillRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ProfileSkillService implements ManageProfileSkillsUseCase {

    private final ProfileSkillRepositoryPort repo;

    public ProfileSkillService(ProfileSkillRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public List<ProfileSkill> getSkills(UUID userId) {
        return repo.findByUserId(userId);
    }

    @Override
    public ProfileSkill addSkill(ProfileSkill skill) {
        return repo.save(skill);
    }

    @Override
    public ProfileSkill updateSkill(ProfileSkill skill) {
        return repo.save(skill);
    }

    @Override
    public void deleteSkill(UUID skillId, UUID userId) {
        repo.findById(skillId)
                .filter(s -> s.userId().equals(userId))
                .ifPresent(s -> repo.deleteById(skillId));
    }
}
