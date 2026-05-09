package com.autoapplicant.port.out.user;

import com.autoapplicant.domain.user.Project;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepositoryPort {
    Project save(Project project);
    List<Project> findByUserId(UUID userId);
    Optional<Project> findById(UUID id);
    void deleteByIdAndUserId(UUID id, UUID userId);
}
