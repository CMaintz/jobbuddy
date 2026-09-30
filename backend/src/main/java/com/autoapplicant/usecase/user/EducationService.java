package com.autoapplicant.usecase.user;

import com.autoapplicant.domain.common.NotFoundException;
import com.autoapplicant.domain.user.Education;
import com.autoapplicant.port.in.user.ManageEducationUseCase;
import com.autoapplicant.port.out.user.EducationRepositoryPort;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class EducationService implements ManageEducationUseCase {

    private final EducationRepositoryPort repo;

    public EducationService(EducationRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public Education addEducation(UUID userId, Education education) {
        Education withUser = new Education(null, userId, education.institution(), education.degree(),
                education.fieldOfStudy(), education.startDate(), education.endDate(),
                education.description(), education.grade(), education.displayOrder(), null, null,
                education.skills());
        return repo.save(withUser);
    }

    @Override
    public Education updateEducation(UUID userId, UUID id, Education education) {
        Education existing = repo.findById(id)
                .filter(e -> userId.equals(e.userId()))
                .orElseThrow(() -> new NotFoundException("Education not found"));
        Education updated = new Education(id, userId, education.institution(), education.degree(),
                education.fieldOfStudy(), education.startDate(), education.endDate(),
                education.description(), education.grade(), education.displayOrder(), existing.createdAt(), null,
                education.skills());
        return repo.save(updated);
    }

    @Override
    public List<Education> getEducation(UUID userId) {
        return repo.findByUserId(userId);
    }

    @Override
    public void deleteEducation(UUID userId, UUID id) {
        repo.deleteByIdAndUserId(id, userId);
    }
}
