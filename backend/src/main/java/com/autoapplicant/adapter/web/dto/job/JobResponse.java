package com.autoapplicant.adapter.web.dto.job;

import com.autoapplicant.domain.job.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JobResponse(
        UUID id,
        String source,
        String url,
        String title,
        String companyName,
        String descriptionClean,
        String employmentType,
        String seniority,
        String remoteType,
        String location,
        String municipality,
        Integer salaryMin,
        Integer salaryMax,
        String currency,
        List<String> technologies,
        List<String> skills,
        List<String> languages,
        Instant postedAt,
        String aiSummary,
        List<String> aiTags,
        String jobCategory,
        String shortDescription,
        java.time.LocalDate applicationDeadline,
        boolean active,
        /** True when descriptionClean holds only the opening of the posting. */
        boolean descriptionTruncated
) {
    /** The whole posting — for a single job the caller asked for by id. */
    public static JobResponse from(Job job) {
        return build(job, job.descriptionClean(), false);
    }

    /**
     * The posting's opening only. Lists use this so a feed of twenty roles does not
     * carry twenty full descriptions; the reader fetches the rest for the one they open.
     */
    public static JobResponse preview(Job job) {
        return build(job, JobText.preview(job.descriptionClean()),
                JobText.exceedsPreview(job.descriptionClean()));
    }

    private static JobResponse build(Job job, String description, boolean truncated) {
        return new JobResponse(
                job.id(),
                job.source() != null ? job.source().name() : null,
                job.url(), job.title(), job.companyName(), description,
                job.employmentType() != null ? job.employmentType().name() : null,
                job.seniority() != null ? job.seniority().name() : null,
                job.remoteType() != null ? job.remoteType().name() : null,
                job.location(), job.municipality(),
                job.salaryMin(), job.salaryMax(), job.currency(),
                job.technologies(), job.skills(), job.languages(),
                job.postedAt(), job.aiSummary(), job.aiTags(),
                job.jobCategory() != null ? job.jobCategory().name() : null,
                job.shortDescription(),
                job.applicationDeadline(),
                job.isActive(),
                truncated
        );
    }
}
