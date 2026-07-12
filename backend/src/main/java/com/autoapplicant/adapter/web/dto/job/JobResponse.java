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
        boolean active
) {
    public static JobResponse from(Job job) {
        return new JobResponse(
                job.id(),
                job.source() != null ? job.source().name() : null,
                job.url(), job.title(), job.companyName(), job.descriptionClean(),
                job.employmentType() != null ? job.employmentType().name() : null,
                job.seniority() != null ? job.seniority().name() : null,
                job.remoteType() != null ? job.remoteType().name() : null,
                job.location(), job.municipality(),
                job.salaryMin(), job.salaryMax(), job.currency(),
                job.technologies(), job.skills(), job.languages(),
                job.postedAt(), job.aiSummary(), job.aiTags(),
                job.jobCategory() != null ? job.jobCategory().name() : null,
                job.shortDescription(),
                job.isActive()
        );
    }
}
