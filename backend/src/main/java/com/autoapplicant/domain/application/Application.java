package com.autoapplicant.domain.application;

import java.time.Instant;
import java.util.UUID;

public record Application(
        UUID id,
        UUID userId,
        UUID jobId,
        ApplicationStatus status,
        Instant appliedAt,
        String recruiterName,
        String recruiterEmail,
        String coverLetterText,
        String applicationText,
        String recruiterMessage,
        UUID cvVersionId,
        UUID promptTemplateId,
        Integer matchScore,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {}
