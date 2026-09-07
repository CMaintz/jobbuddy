package com.autoapplicant.port.out.skills;

import com.autoapplicant.domain.skill.ProfileSkill;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfileSkillRepositoryPort {
    List<ProfileSkill> findByUserId(UUID userId);
    Optional<ProfileSkill> findById(UUID id);
    ProfileSkill save(ProfileSkill skill);
    void deleteById(UUID id);
}
