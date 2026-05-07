package com.autoapplicant.usecase.application;

import com.autoapplicant.domain.application.Application;
import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.port.in.application.*;
import com.autoapplicant.port.out.application.ApplicationRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ApplicationService implements
        CreateApplicationUseCase, UpdateApplicationStatusUseCase,
        GetApplicationsUseCase, GetApplicationByIdUseCase {

    private final ApplicationRepositoryPort repo;

    public ApplicationService(ApplicationRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public Application createApplication(UUID userId, UUID jobId, UUID cvVersionId, String notes) {
        Application app = new Application(null, userId, jobId, ApplicationStatus.SAVED,
                null, null, null, null, null, null,
                cvVersionId, null, null, notes, null, null);
        return repo.save(app);
    }

    @Override
    public Application updateStatus(UUID applicationId, UUID userId, ApplicationStatus newStatus, String notes) {
        Application existing = repo.findByIdAndUserId(applicationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));

        if (!existing.status().canTransitionTo(newStatus)) {
            throw new IllegalStateException("Invalid status transition: "
                    + existing.status() + " -> " + newStatus);
        }

        Application updated = new Application(
                existing.id(), existing.userId(), existing.jobId(), newStatus,
                newStatus == ApplicationStatus.APPLIED ? java.time.Instant.now() : existing.appliedAt(),
                existing.recruiterName(), existing.recruiterEmail(),
                existing.coverLetterText(), existing.applicationText(), existing.recruiterMessage(),
                existing.cvVersionId(), existing.promptTemplateId(), existing.matchScore(),
                notes != null ? notes : existing.notes(),
                existing.createdAt(), java.time.Instant.now());
        return repo.save(updated);
    }

    @Override
    public List<Application> getApplications(UUID userId) {
        return repo.findByUserId(userId);
    }

    @Override
    public Optional<Application> getApplicationById(UUID id, UUID userId) {
        return repo.findByIdAndUserId(id, userId);
    }
}
