package com.autoapplicant.usecase.user;

import com.autoapplicant.domain.user.Project;
import com.autoapplicant.port.in.user.ManageProjectsUseCase;
import com.autoapplicant.port.out.user.ProjectRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ProjectService implements ManageProjectsUseCase {

    private final ProjectRepositoryPort repo;

    public ProjectService(ProjectRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public Project addProject(UUID userId, Project project) {
        Project withUser = new Project(null, userId, project.name(), project.description(),
                project.technologies(), project.githubUrl(), project.liveUrl(),
                project.architectureNotes(), project.measurableOutcomes(), project.businessImpact(),
                project.startDate(), project.endDate(), project.isFeatured(),
                project.displayOrder(), null, null, project.skills());
        return repo.save(withUser);
    }

    @Override
    public Project updateProject(UUID userId, UUID id, Project project) {
        Project updated = new Project(id, userId, project.name(), project.description(),
                project.technologies(), project.githubUrl(), project.liveUrl(),
                project.architectureNotes(), project.measurableOutcomes(), project.businessImpact(),
                project.startDate(), project.endDate(), project.isFeatured(),
                project.displayOrder(), null, null, project.skills());
        return repo.save(updated);
    }

    @Override
    public List<Project> getProjects(UUID userId) {
        return repo.findByUserId(userId);
    }

    @Override
    public void deleteProject(UUID userId, UUID id) {
        repo.deleteByIdAndUserId(id, userId);
    }
}
