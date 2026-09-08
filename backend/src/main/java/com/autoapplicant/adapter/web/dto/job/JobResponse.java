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
        boolean descriptionTruncated,
        /**
         * What the posting asks for, in its own words. A list response carries the demands
         * only — those decide whether a role is worth opening at all; the preferences arrive
         * with the full posting.
         */
        List<JobRequirementResponse> requirements
) {

    /**
     * One ask from the posting. {@code kind} says how it could be verified at all — only
     * {@code SKILL} is something a keyword check can settle.
     */
    public record JobRequirementResponse(String text, String tier, String kind, String skill) {
        static JobRequirementResponse from(JobRequirement r) {
            return new JobRequirementResponse(r.text(), r.tier().name(), r.kind().name(), r.skill());
        }
    }

    /** At most this many asks ride along in a list response. */
    private static final int PREVIEW_REQUIREMENTS = 10;

    /** The whole posting — for a single job the caller asked for by id. */
    public static JobResponse from(Job job) {
        return build(job, job.descriptionClean(), false, requirements(job, false));
    }

    /**
     * The posting's opening only. Lists use this so a feed of twenty roles does not
     * carry twenty full descriptions; the reader fetches the rest for the one they open.
     */
    public static JobResponse preview(Job job) {
        return build(job, JobText.preview(job.descriptionClean()),
                JobText.exceedsPreview(job.descriptionClean()), requirements(job, true));
    }

    private static List<JobRequirementResponse> requirements(Job job, boolean demandsOnly) {
        List<JobRequirement> source = job.requirements();
        if (source == null || source.isEmpty()) return List.of();
        return source.stream()
                .filter(r -> !demandsOnly || r.isRequired())
                .limit(demandsOnly ? PREVIEW_REQUIREMENTS : Integer.MAX_VALUE)
                .map(JobRequirementResponse::from)
                .toList();
    }

    private static JobResponse build(Job job, String description, boolean truncated,
                                     List<JobRequirementResponse> requirements) {
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
                truncated,
                requirements
        );
    }
}
