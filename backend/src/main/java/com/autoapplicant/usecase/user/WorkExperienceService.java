package com.autoapplicant.usecase.user;

import com.autoapplicant.domain.user.WorkExperience;
import com.autoapplicant.port.in.user.ManageWorkExperienceUseCase;
import com.autoapplicant.port.out.user.WorkExperienceRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class WorkExperienceService implements ManageWorkExperienceUseCase {

    private final WorkExperienceRepositoryPort repo;

    public WorkExperienceService(WorkExperienceRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public WorkExperience addWorkExperience(UUID userId, WorkExperience experience) {
        WorkExperience withUser = new WorkExperience(null, userId, experience.companyName(),
                experience.title(), experience.location(), experience.description(),
                experience.startDate(), experience.endDate(), experience.isCurrent(),
                experience.technologies(), experience.achievements(),
                experience.displayOrder(), null, null, experience.skills());
        return repo.save(withUser);
    }

    @Override
    public WorkExperience updateWorkExperience(UUID userId, UUID id, WorkExperience experience) {
        WorkExperience updated = new WorkExperience(id, userId, experience.companyName(),
                experience.title(), experience.location(), experience.description(),
                experience.startDate(), experience.endDate(), experience.isCurrent(),
                experience.technologies(), experience.achievements(),
                experience.displayOrder(), null, null, experience.skills());
        return repo.save(updated);
    }

    @Override
    public List<WorkExperience> getWorkExperience(UUID userId) {
        return repo.findByUserId(userId);
    }

    @Override
    public void deleteWorkExperience(UUID userId, UUID id) {
        repo.deleteByIdAndUserId(id, userId);
    }
}
