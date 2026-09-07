package com.autoapplicant.port.in.application;

import com.autoapplicant.domain.application.Application;
import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.domain.application.CreateApplicationCommand;

import java.util.UUID;

public interface CreateApplicationUseCase {
    Application createApplication(UUID userId, UUID jobId, UUID cvVersionId, String notes);
    Application createApplication(CreateApplicationCommand command);
    Application attachGeneratedDocument(UUID applicationId, UUID userId, UUID generatedDocumentId,
                                        String generatedContent, ApplicationStatus status,
                                        String notes);
}
