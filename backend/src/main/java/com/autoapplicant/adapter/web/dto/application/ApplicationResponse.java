package com.autoapplicant.adapter.web.dto.application;

import com.autoapplicant.domain.application.Application;
import com.autoapplicant.domain.job.Job;

import java.time.Instant;
import java.util.UUID;

public record ApplicationResponse(
        UUID id,
        UUID jobId,
        String status,
        Instant appliedAt,
        String recruiterName,
        String recruiterEmail,
        String coverLetterText,
        String applicationText,
        String recruiterMessage,
        Integer matchScore,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        String jobTitle,
        String jobCompanyName
) {
    public static ApplicationResponse from(Application a) {
        return new ApplicationResponse(a.id(), a.jobId(), a.status().name(),
                a.appliedAt(), a.recruiterName(), a.recruiterEmail(),
                a.coverLetterText(), a.applicationText(), a.recruiterMessage(),
                a.matchScore(), a.notes(), a.createdAt(), a.updatedAt(),
                null, null);
    }

    public static ApplicationResponse from(Application a, Job job) {
        return new ApplicationResponse(a.id(), a.jobId(), a.status().name(),
                a.appliedAt(), a.recruiterName(), a.recruiterEmail(),
                a.coverLetterText(), a.applicationText(), a.recruiterMessage(),
                a.matchScore(), a.notes(), a.createdAt(), a.updatedAt(),
                job != null ? job.title() : null,
                job != null ? job.companyName() : null);
    }
}
