package com.autoapplicant.usecase.application;

import com.autoapplicant.domain.analytics.ResponseMetric;
import com.autoapplicant.domain.application.Application;
import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.domain.application.CreateApplicationCommand;
import com.autoapplicant.domain.document.GeneratedDocument;
import com.autoapplicant.port.in.application.*;
import com.autoapplicant.port.out.analytics.ResponseMetricRepositoryPort;
import com.autoapplicant.port.out.application.ApplicationRepositoryPort;
import com.autoapplicant.usecase.document.StructuredGeneratedDocumentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ApplicationService implements
        CreateApplicationUseCase, UpdateApplicationStatusUseCase,
        UpdateRecruiterInfoUseCase, GetApplicationsUseCase, GetApplicationByIdUseCase {

    private final ApplicationRepositoryPort repo;
    private final ResponseMetricRepositoryPort responseMetricRepo;
    private final StructuredGeneratedDocumentService structuredGeneratedDocuments;

    public ApplicationService(ApplicationRepositoryPort repo,
                              ResponseMetricRepositoryPort responseMetricRepo,
                              StructuredGeneratedDocumentService structuredGeneratedDocuments) {
        this.repo = repo;
        this.responseMetricRepo = responseMetricRepo;
        this.structuredGeneratedDocuments = structuredGeneratedDocuments;
    }

    @Override
    public Application createApplication(UUID userId, UUID jobId, UUID cvVersionId, String notes) {
        return createApplication(new CreateApplicationCommand(
                userId, jobId, cvVersionId, null, null, ApplicationStatus.SAVED,
                null, null, null, null, notes));
    }

    @Override
    public Application createApplication(CreateApplicationCommand command) {
        ApplicationStatus status = command.status() != null ? command.status() : ApplicationStatus.SAVED;
        Application app = new Application(null, command.userId(), command.jobId(), status,
                status == ApplicationStatus.APPLIED ? Instant.now() : null,
                null, null, command.coverLetterText(), command.applicationText(), command.recruiterMessage(),
                null, command.cvVersionId(), command.promptTemplateId(), command.matchScore(), command.notes(), null, null);
        Application saved = repo.save(app);
        if (command.generatedDocumentId() != null) {
            structuredGeneratedDocuments.attachToApplication(command.userId(), command.generatedDocumentId(), saved.id());
        }
        return saved;
    }

    @Override
    public Application attachGeneratedDocument(UUID applicationId, UUID userId, UUID generatedDocumentId,
                                               String generatedContent, ApplicationStatus status, String notes) {
        Application existing = repo.findByIdAndUserId(applicationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        GeneratedDocument document = structuredGeneratedDocuments.attachToApplication(userId, generatedDocumentId, applicationId);
        Application withContent = applyGeneratedContent(existing, document, generatedContent);
        ApplicationStatus targetStatus = status != null ? status : withContent.status();
        Application updated = new Application(
                withContent.id(), withContent.userId(), withContent.jobId(), targetStatus,
                targetStatus == ApplicationStatus.APPLIED && withContent.appliedAt() == null ? Instant.now() : withContent.appliedAt(),
                withContent.recruiterName(), withContent.recruiterEmail(),
                withContent.coverLetterText(), withContent.applicationText(), withContent.recruiterMessage(),
                withContent.recruiterReply(), withContent.cvVersionId(), withContent.promptTemplateId(), withContent.matchScore(),
                notes != null ? notes : withContent.notes(),
                withContent.createdAt(), Instant.now());
        return repo.save(updated);
    }

    private Application applyGeneratedContent(Application application, GeneratedDocument document, String generatedContent) {
        String content = generatedContent != null && !generatedContent.isBlank()
                ? generatedContent
                : document.content();
        String coverLetterText = application.coverLetterText();
        String applicationText = application.applicationText();
        String recruiterMessage = application.recruiterMessage();

        switch (document.documentType()) {
            case COVER_LETTER -> coverLetterText = content;
            case APPLICATION_TEXT -> applicationText = content;
            case RECRUITER_MESSAGE, FOLLOW_UP_MESSAGE -> recruiterMessage = content;
            default -> {
                // CVs are attached as generated documents; application text fields stay unchanged.
            }
        }

        return new Application(
                application.id(), application.userId(), application.jobId(), application.status(),
                application.appliedAt(), application.recruiterName(), application.recruiterEmail(),
                coverLetterText, applicationText, recruiterMessage,
                application.recruiterReply(), application.cvVersionId(), application.promptTemplateId(), application.matchScore(),
                application.notes(), application.createdAt(), application.updatedAt());
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
                existing.recruiterReply(), existing.cvVersionId(), existing.promptTemplateId(), existing.matchScore(),
                notes != null ? notes : existing.notes(),
                existing.createdAt(), java.time.Instant.now());
        Application saved = repo.save(updated);

        String eventType = switch (newStatus) {
            case RECRUITER_CONTACT -> "RECRUITER_CONTACT";
            case INTERVIEW -> "INTERVIEW_SCHEDULED";
            case TECHNICAL_TEST -> "TECHNICAL_TEST";
            case FINAL_ROUND -> "FINAL_ROUND";
            case OFFER -> "OFFER_RECEIVED";
            case REJECTED -> "REJECTED";
            default -> null;
        };
        if (eventType != null) {
            responseMetricRepo.save(new ResponseMetric(null, existing.userId(), existing.jobId(),
                    applicationId, eventType, Instant.now(), notes));
        }

        return saved;
    }

    @Override
    public Application updateRecruiterInfo(UUID applicationId, UUID userId,
                                           String recruiterName, String recruiterEmail,
                                           String recruiterMessage, String recruiterReply) {
        Application existing = repo.findByIdAndUserId(applicationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        Application updated = new Application(
                existing.id(), existing.userId(), existing.jobId(), existing.status(),
                existing.appliedAt(),
                recruiterName    != null ? recruiterName    : existing.recruiterName(),
                recruiterEmail   != null ? recruiterEmail   : existing.recruiterEmail(),
                existing.coverLetterText(), existing.applicationText(),
                recruiterMessage != null ? recruiterMessage : existing.recruiterMessage(),
                recruiterReply   != null ? recruiterReply   : existing.recruiterReply(),
                existing.cvVersionId(), existing.promptTemplateId(), existing.matchScore(),
                existing.notes(), existing.createdAt(), Instant.now());
        return repo.save(updated);
    }

    @Override
    public List<Application> getApplications(UUID userId) {
        return repo.findByUserId(userId);
    }

    @Override
    public Page<Application> getApplications(UUID userId, Pageable pageable) {
        return repo.findByUserId(userId, pageable);
    }

    @Override
    public Optional<Application> getApplicationById(UUID id, UUID userId) {
        return repo.findByIdAndUserId(id, userId);
    }
}
