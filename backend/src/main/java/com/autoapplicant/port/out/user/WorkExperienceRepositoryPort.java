package com.autoapplicant.port.out.user;

import com.autoapplicant.domain.user.WorkExperience;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkExperienceRepositoryPort {
    WorkExperience save(WorkExperience experience);
    List<WorkExperience> findByUserId(UUID userId);
    Optional<WorkExperience> findById(UUID id);
    void deleteByIdAndUserId(UUID id, UUID userId);
}
