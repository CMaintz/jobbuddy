package com.autoapplicant.adapter.persistence.mapper;

import com.autoapplicant.adapter.persistence.entity.JobEntity;
import com.autoapplicant.domain.job.*;
import com.autoapplicant.domain.job.JobRequirement;
import com.autoapplicant.domain.job.RequirementKind;
import com.autoapplicant.domain.job.RequirementTier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
                e.getApplicationDeadline(),
                JobContact.ofNullable(e.getContactName(), e.getContactTitle(),
                        e.getContactEmail(), e.getContactPhone()),
                toList(e.getRequiredSkills()),
                toList(e.getPreferredSkills()),
                toRequirements(e.getRequirements())
        );
    }

    /**
     * Copies what enrichment produced from {@code from} onto {@code to}, the row as it stands
     * now. Extracted fields are enrichment's own and replace what is there. Fields enrichment
     * only fills in when the crawler left them empty are filled against the current row, not
     * the one enrichment read: the crawl may have set them (a deadline) during the AI call.
     */
    public static void copyEnrichmentFields(JobEntity from, JobEntity to) {
        to.setDescriptionClean(from.getDescriptionClean());
        to.setTechnologies(from.getTechnologies());
        to.setSkills(from.getSkills());
        to.setRequiredSkills(from.getRequiredSkills());
        to.setPreferredSkills(from.getPreferredSkills());
        to.setRequirements(from.getRequirements());
        to.setAiSummary(from.getAiSummary());
        to.setAiTags(from.getAiTags());
        to.setAiSeniorityEstimate(from.getAiSeniorityEstimate());
        fillGaps(from, to);
    }

    private static void fillGaps(JobEntity from, JobEntity to) {
        to.setShortDescription(firstNonNull(to.getShortDescription(), from.getShortDescription()));
        to.setEmploymentType(firstNonNull(to.getEmploymentType(), from.getEmploymentType()));
        to.setRemoteType(firstNonNull(to.getRemoteType(), from.getRemoteType()));
        to.setMunicipality(firstNonNull(to.getMunicipality(), from.getMunicipality()));
        to.setSalaryMin(firstNonNull(to.getSalaryMin(), from.getSalaryMin()));
        to.setSalaryMax(firstNonNull(to.getSalaryMax(), from.getSalaryMax()));
        to.setCurrency(firstNonNull(to.getCurrency(), from.getCurrency()));
        to.setApplicationDeadline(firstNonNull(to.getApplicationDeadline(), from.getApplicationDeadline()));
        if (to.getJobCategory() == null || JobCategory.OTHER.name().equals(to.getJobCategory())) {
            to.setJobCategory(from.getJobCategory());
        }
        if (to.getContactName() == null && to.getContactEmail() == null && to.getContactPhone() == null) {
            to.setContactName(from.getContactName());
            to.setContactTitle(from.getContactTitle());
            to.setContactEmail(from.getContactEmail());
            to.setContactPhone(from.getContactPhone());
        }
    }

    private static <T> T firstNonNull(T current, T produced) {
        return current != null ? current : produced;
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
        e.setRequiredSkills(toArray(d.requiredSkills()));
        e.setRequirements(fromRequirements(d.requirements()));
        e.setPreferredSkills(toArray(d.preferredSkills()));
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
        JobContact contact = d.contact();
        e.setContactName(contact != null ? contact.name() : null);
        e.setContactTitle(contact != null ? contact.title() : null);
        e.setContactEmail(contact != null ? contact.email() : null);
        e.setContactPhone(contact != null ? contact.phone() : null);
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
    /** jsonb rows in, domain requirements out. A malformed entry is skipped, never fatal. */
    private static List<JobRequirement> toRequirements(List<Map<String, Object>> raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        List<JobRequirement> result = new java.util.ArrayList<>();
        for (Map<String, Object> row : raw) {
            if (row == null) continue;
            Object text = row.get("text");
            if (!(text instanceof String s) || s.isBlank()) continue;
            result.add(new JobRequirement(s,
                    RequirementTier.parse((String) row.get("tier")),
                    RequirementKind.parse((String) row.get("kind")),
                    (String) row.get("skill")));
        }
        return List.copyOf(result);
    }

    private static List<Map<String, Object>> fromRequirements(List<JobRequirement> requirements) {
        if (requirements == null || requirements.isEmpty()) return new java.util.ArrayList<>();
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (JobRequirement r : requirements) {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("text", r.text());
            row.put("tier", r.tier().name());
            row.put("kind", r.kind().name());
            if (r.skill() != null) row.put("skill", r.skill());
            rows.add(row);
        }
        return rows;
    }
}
