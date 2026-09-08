package com.autoapplicant.usecase.application;

import com.autoapplicant.domain.application.Application;
import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.domain.application.ApplicationStatusEvent;
import com.autoapplicant.domain.application.CreateApplicationCommand;
import com.autoapplicant.domain.document.GeneratedDocument;
import com.autoapplicant.port.in.application.*;
import com.autoapplicant.port.in.document.PersistGeneratedDocumentUseCase;
import com.autoapplicant.port.out.application.ApplicationRepositoryPort;
import com.autoapplicant.port.out.application.ApplicationStatusEventRepositoryPort;
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
        UpdateRecruiterInfoUseCase, UpdateOutcomeUseCase,
        GetApplicationsUseCase, GetApplicationByIdUseCase {

    private final ApplicationRepositoryPort repo;
    private final ApplicationStatusEventRepositoryPort statusEventRepo;
    private final PersistGeneratedDocumentUseCase structuredGeneratedDocuments;

    public ApplicationService(ApplicationRepositoryPort repo,
                              ApplicationStatusEventRepositoryPort statusEventRepo,
                              PersistGeneratedDocumentUseCase structuredGeneratedDocuments) {
        this.repo = repo;
        this.statusEventRepo = statusEventRepo;
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
        if (command.jobId() != null && repo.existsByUserIdAndJobId(command.userId(), command.jobId())) {
            throw new IllegalStateException("An application for this job already exists");
        }
        ApplicationStatus status = command.status() != null ? command.status() : ApplicationStatus.SAVED;
        Instant now = Instant.now();
        Application app = new Application(null, command.userId(), command.jobId(), status,
                status == ApplicationStatus.APPLIED ? now : null,
                null, null, command.coverLetterText(), command.applicationText(), command.recruiterMessage(),
                null, command.cvVersionId(), command.promptTemplateId(), command.matchScore(), command.notes(), null, null,
                null, null);
        Application saved = repo.save(app);
        // The opening move belongs in the ledger too, or the timeline starts mid-story and
        // funnel velocity never sees how long the first stage took.
        statusEventRepo.save(new ApplicationStatusEvent(
                null, saved.id(), saved.userId(), null, status, now, command.notes()));
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
        Instant now = Instant.now();
        Application updated = withContent.toBuilder()
                .status(targetStatus)
                .appliedAt(targetStatus == ApplicationStatus.APPLIED && withContent.appliedAt() == null
                        ? now : withContent.appliedAt())
                .notes(notes != null ? notes : withContent.notes())
                .updatedAt(now)
                .build();
        Application saved = repo.save(updated);
        // Attaching a generated document can move the application on; that is a move like
        // any other and has to reach the ledger, or the timeline skips it.
        if (targetStatus != existing.status()) {
            statusEventRepo.save(new ApplicationStatusEvent(
                    null, saved.id(), saved.userId(), existing.status(), targetStatus, now, notes));
        }
        return saved;
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
            case APPLICATION_TEXT, UNSOLICITED_APPLICATION -> applicationText = content;
            case RECRUITER_MESSAGE, FOLLOW_UP_MESSAGE -> recruiterMessage = content;
            default -> {
                // CVs are attached as generated documents; application text fields stay unchanged.
            }
        }

        return application.toBuilder()
                .coverLetterText(coverLetterText)
                .applicationText(applicationText)
                .recruiterMessage(recruiterMessage)
                .build();
    }

    @Override
    public Application updateStatus(UUID applicationId, UUID userId, ApplicationStatus newStatus, String notes) {
        Application existing = repo.findByIdAndUserId(applicationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));

        if (!existing.status().canTransitionTo(newStatus)) {
            throw new IllegalStateException("Invalid status transition: "
                    + existing.status() + " -> " + newStatus);
        }

        // One instant for the whole transition: the response metric's note finds its way
        // back to the ledger entry by matching on it.
        Instant now = Instant.now();
        Application updated = existing.toBuilder()
                .status(newStatus)
                .appliedAt(newStatus == ApplicationStatus.APPLIED ? now : existing.appliedAt())
                .notes(notes != null ? notes : existing.notes())
                .updatedAt(now)
                .build();
        Application saved = repo.save(updated);

        // Append-only transition ledger — every change, for the timeline and funnel velocity.
        statusEventRepo.save(new ApplicationStatusEvent(
                null, applicationId, existing.userId(), existing.status(), newStatus, now, notes));

        return saved;
    }

    @Override
    public Application updateRecruiterInfo(UUID applicationId, UUID userId,
                                           String recruiterName, String recruiterEmail,
                                           String recruiterMessage, String recruiterReply) {
        Application existing = repo.findByIdAndUserId(applicationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        Application updated = existing.toBuilder()
                .recruiterName(recruiterName    != null ? recruiterName    : existing.recruiterName())
                .recruiterEmail(recruiterEmail   != null ? recruiterEmail   : existing.recruiterEmail())
                .recruiterMessage(recruiterMessage != null ? recruiterMessage : existing.recruiterMessage())
                .recruiterReply(recruiterReply   != null ? recruiterReply   : existing.recruiterReply())
                .updatedAt(Instant.now())
                .build();
        return repo.save(updated);
    }

    @Override
    public Application updateOutcome(UUID applicationId, UUID userId, String outcomeFeedback, String outcomeLessons) {
        Application existing = repo.findByIdAndUserId(applicationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        Application updated = existing.toBuilder()
                .outcomeFeedback(outcomeFeedback != null ? outcomeFeedback : existing.outcomeFeedback())
                .outcomeLessons(outcomeLessons  != null ? outcomeLessons  : existing.outcomeLessons())
                .updatedAt(Instant.now())
                .build();
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
