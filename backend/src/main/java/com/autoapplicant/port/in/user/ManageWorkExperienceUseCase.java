package com.autoapplicant.port.in.user;

import com.autoapplicant.domain.user.WorkExperience;

import java.util.List;
import java.util.UUID;

public interface ManageWorkExperienceUseCase {
    WorkExperience addWorkExperience(UUID userId, WorkExperience experience);
    WorkExperience updateWorkExperience(UUID userId, UUID id, WorkExperience experience);
    List<WorkExperience> getWorkExperience(UUID userId);
    void deleteWorkExperience(UUID userId, UUID id);
}
