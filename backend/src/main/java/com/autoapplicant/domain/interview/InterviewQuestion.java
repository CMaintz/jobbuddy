package com.autoapplicant.domain.interview;

import java.time.Instant;
import java.util.UUID;

public record InterviewQuestion(
        UUID id,
        UUID jobId,
        UUID userId,
        String question,
        String category,
        String starAnswer,
        boolean practiced,
        int displayOrder,
        Instant createdAt,
        Instant updatedAt
) {}
