package com.autoapplicant.domain.application;

import java.util.UUID;

public record CreateApplicationCommand(
        UUID userId,
        UUID jobId,
        UUID cvVersionId,
        UUID promptTemplateId,
        UUID generatedDocumentId,
        ApplicationStatus status,
        String coverLetterText,
        String applicationText,
        String recruiterMessage,
        Integer matchScore,
        String notes
) {}
