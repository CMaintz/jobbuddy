package com.autoapplicant.port.in.user;

import com.autoapplicant.domain.user.Project;

import java.util.List;
import java.util.UUID;

public interface ManageProjectsUseCase {
    Project addProject(UUID userId, Project project);
    Project updateProject(UUID userId, UUID id, Project project);
    List<Project> getProjects(UUID userId);
    void deleteProject(UUID userId, UUID id);
}
