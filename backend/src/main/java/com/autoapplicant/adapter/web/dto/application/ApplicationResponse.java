package com.autoapplicant.adapter.web.dto.application;

import com.autoapplicant.domain.application.Application;

import java.time.Instant;
import java.util.UUID;

public record ApplicationResponse(
        UUID id,
        UUID jobId,
        String status,
        Instant appliedAt,
        String recruiterName,
        String recruiterEmail,
        Integer matchScore,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
    public static ApplicationResponse from(Application a) {
        return new ApplicationResponse(a.id(), a.jobId(), a.status().name(),
                a.appliedAt(), a.recruiterName(), a.recruiterEmail(),
                a.matchScore(), a.notes(), a.createdAt(), a.updatedAt());
    }
}
