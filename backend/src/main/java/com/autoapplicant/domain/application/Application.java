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
        String recruiterReply,
        UUID cvVersionId,
        UUID promptTemplateId,
        Integer matchScore,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        /** Feedback received from the company (rejection reasons, interviewer comments). */
        String outcomeFeedback,
        /** What to do differently next time — fed back into future generations. */
        String outcomeLessons
) {}
