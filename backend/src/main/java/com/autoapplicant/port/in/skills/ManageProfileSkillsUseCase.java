package com.autoapplicant.port.in.skills;

import com.autoapplicant.domain.skill.ProfileSkill;

import java.util.List;
import java.util.UUID;

public interface ManageProfileSkillsUseCase {
    List<ProfileSkill> getSkills(UUID userId);
    ProfileSkill addSkill(ProfileSkill skill);
    ProfileSkill updateSkill(ProfileSkill skill);
    void deleteSkill(UUID skillId, UUID userId);
}
