package com.autoapplicant.adapter.web.dto.application;

import com.autoapplicant.domain.application.Application;
import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.domain.job.Job;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
        String recruiterReply,
        Integer matchScore,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        String outcomeFeedback,
        String outcomeLessons,
        String jobTitle,
        String jobCompanyName,
        LocalDate deadline,
        LocalDateTime interviewAt,
        /** The statuses this application may move to next; empty once it is closed. */
        List<String> nextStatuses
) {
    public static ApplicationResponse from(Application a) {
        return new ApplicationResponse(a.id(), a.jobId(), a.status().name(),
                a.appliedAt(), a.recruiterName(), a.recruiterEmail(),
                a.coverLetterText(), a.applicationText(), a.recruiterMessage(), a.recruiterReply(),
                a.matchScore(), a.notes(), a.createdAt(), a.updatedAt(),
                a.outcomeFeedback(), a.outcomeLessons(),
                null, null,
                a.deadline(), a.interviewAt(), nextStatuses(a));
    }

    public static ApplicationResponse from(Application a, Job job) {
        return new ApplicationResponse(a.id(), a.jobId(), a.status().name(),
                a.appliedAt(), a.recruiterName(), a.recruiterEmail(),
                a.coverLetterText(), a.applicationText(), a.recruiterMessage(), a.recruiterReply(),
                a.matchScore(), a.notes(), a.createdAt(), a.updatedAt(),
                a.outcomeFeedback(), a.outcomeLessons(),
                job != null ? job.title() : null,
                job != null ? job.companyName() : null,
                a.deadline(), a.interviewAt(), nextStatuses(a));
    }

    private static List<String> nextStatuses(Application a) {
        return a.status().nextStatuses().stream().map(ApplicationStatus::name).toList();
    }
}
