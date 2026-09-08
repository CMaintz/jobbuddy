package com.autoapplicant.adapter.persistence.mapper;

import com.autoapplicant.adapter.persistence.entity.JobEntity;
import com.autoapplicant.domain.job.*;

import java.util.Arrays;
import java.util.List;

public final class JobMapper {

    private JobMapper() {}

    public static Job toDomain(JobEntity e) {
        return new Job(
                e.getId(),
                e.getSource() != null ? JobSource.valueOf(e.getSource()) : null,
                e.getSourceJobId(),
                e.getUrl(),
                e.getTitle(),
                e.getCompanyId(),
                e.getCompanyName(),
                e.getDescriptionRaw(),
                e.getDescriptionClean(),
                parseEnum(e.getEmploymentType(), EmploymentType.class),
                parseEnum(e.getSeniority(), Seniority.class),
                parseEnum(e.getRemoteType(), RemoteType.class),
                e.getLocation(),
                e.getMunicipality(),
                e.getRegion(),
                e.getCountry(),
                e.getSalaryMin(),
                e.getSalaryMax(),
                e.getCurrency(),
                toList(e.getTechnologies()),
                toList(e.getSkills()),
                toList(e.getLanguages()),
                e.getPostedAt(),
                e.getScrapedAt(),
                e.getAiSummary(),
                toList(e.getAiTags()),
                e.getAiSeniorityEstimate(),
                e.getDuplicateGroupId(),
                e.isActive(),
                e.getJobCategory() != null ? parseEnum(e.getJobCategory(), JobCategory.class) : null,
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getShortDescription(),
                e.getLastSeenAt(),
                e.getApplicationDeadline()
        );
    }

    public static JobEntity toEntity(Job d) {
        JobEntity e = new JobEntity();
        e.setId(d.id());
        e.setSource(d.source() != null ? d.source().name() : null);
        e.setSourceJobId(d.sourceJobId());
        e.setUrl(d.url());
        e.setTitle(d.title());
        e.setCompanyId(d.companyId());
        e.setCompanyName(d.companyName());
        e.setDescriptionRaw(d.descriptionRaw());
        e.setDescriptionClean(d.descriptionClean());
        e.setEmploymentType(d.employmentType() != null ? d.employmentType().name() : null);
        e.setSeniority(d.seniority() != null ? d.seniority().name() : null);
        e.setRemoteType(d.remoteType() != null ? d.remoteType().name() : null);
        e.setLocation(d.location());
        e.setMunicipality(d.municipality());
        e.setRegion(d.region());
        e.setCountry(d.country());
        e.setSalaryMin(d.salaryMin());
        e.setSalaryMax(d.salaryMax());
        e.setCurrency(d.currency());
        e.setTechnologies(toArray(d.technologies()));
        e.setSkills(toArray(d.skills()));
        e.setLanguages(toArray(d.languages()));
        e.setPostedAt(d.postedAt());
        e.setScrapedAt(d.scrapedAt());
        e.setAiSummary(d.aiSummary());
        e.setAiTags(toArray(d.aiTags()));
        e.setAiSeniorityEstimate(d.aiSeniorityEstimate());
        e.setDuplicateGroupId(d.duplicateGroupId());
        e.setActive(d.isActive());
        e.setJobCategory(d.jobCategory() != null ? d.jobCategory().name() : null);
        e.setShortDescription(d.shortDescription());
        e.setLastSeenAt(d.lastSeenAt());
        e.setApplicationDeadline(d.applicationDeadline());
        return e;
    }

    private static <T extends Enum<T>> T parseEnum(String value, Class<T> clazz) {
        if (value == null) return null;
        try { return Enum.valueOf(clazz, value); } catch (IllegalArgumentException ex) { return null; }
    }

    private static List<String> toList(String[] arr) {
        return arr != null ? Arrays.asList(arr) : List.of();
    }

    private static String[] toArray(List<String> list) {
        return list != null ? list.toArray(String[]::new) : new String[0];
    }
}
