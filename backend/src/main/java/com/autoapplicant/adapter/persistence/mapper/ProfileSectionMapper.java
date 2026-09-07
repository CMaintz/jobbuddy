package com.autoapplicant.adapter.persistence.mapper;

import com.autoapplicant.adapter.persistence.entity.*;
import com.autoapplicant.domain.skill.SkillTaxonomy;
import com.autoapplicant.domain.user.*;

import java.util.Arrays;
import java.util.List;

public final class ProfileSectionMapper {

    private ProfileSectionMapper() {}

    // ── WorkExperience ────────────────────────────────────────────────────────

    public static WorkExperience toDomain(WorkExperienceEntity e) {
        return toDomain(e, List.of());
    }

    public static WorkExperience toDomain(WorkExperienceEntity e, List<SkillTaxonomy> skills) {
        return new WorkExperience(e.getId(), e.getUserId(), e.getCompanyName(), e.getTitle(),
                e.getLocation(), e.getDescription(), e.getStartDate(), e.getEndDate(), e.isCurrent(),
                toList(e.getTechnologies()), toList(e.getAchievements()),
                e.getDisplayOrder(), e.getCreatedAt(), e.getUpdatedAt(),
                skills != null ? skills : List.of());
    }

    public static WorkExperienceEntity toEntity(WorkExperience d) {
        WorkExperienceEntity e = new WorkExperienceEntity();
        e.setId(d.id());
        e.setUserId(d.userId());
        e.setCompanyName(d.companyName());
        e.setTitle(d.title());
        e.setLocation(d.location());
        e.setDescription(d.description());
        e.setStartDate(d.startDate());
        e.setEndDate(d.endDate());
        e.setCurrent(d.isCurrent());
        e.setTechnologies(toArray(d.technologies()));
        e.setAchievements(toArray(d.achievements()));
        e.setDisplayOrder(d.displayOrder());
        return e;
    }

    // ── Project ───────────────────────────────────────────────────────────────

    public static Project toDomain(ProjectEntity e) {
        return toDomain(e, List.of());
    }

    public static Project toDomain(ProjectEntity e, List<SkillTaxonomy> skills) {
        return new Project(e.getId(), e.getUserId(), e.getName(), e.getDescription(),
                toList(e.getTechnologies()), e.getGithubUrl(), e.getLiveUrl(),
                e.getArchitectureNotes(), e.getMeasurableOutcomes(), e.getBusinessImpact(),
                e.getStartDate(), e.getEndDate(), e.isFeatured(), e.getDisplayOrder(),
                e.getCreatedAt(), e.getUpdatedAt(),
                skills != null ? skills : List.of());
    }

    public static ProjectEntity toEntity(Project d) {
        ProjectEntity e = new ProjectEntity();
        e.setId(d.id());
        e.setUserId(d.userId());
        e.setName(d.name());
        e.setDescription(d.description());
        e.setTechnologies(toArray(d.technologies()));
        e.setGithubUrl(d.githubUrl());
        e.setLiveUrl(d.liveUrl());
        e.setArchitectureNotes(d.architectureNotes());
        e.setMeasurableOutcomes(d.measurableOutcomes());
        e.setBusinessImpact(d.businessImpact());
        e.setStartDate(d.startDate());
        e.setEndDate(d.endDate());
        e.setFeatured(d.isFeatured());
        e.setDisplayOrder(d.displayOrder());
        return e;
    }

    // ── Education ─────────────────────────────────────────────────────────────

    public static Education toDomain(EducationEntity e) {
        return toDomain(e, List.of());
    }

    public static Education toDomain(EducationEntity e, List<SkillTaxonomy> skills) {
        return new Education(e.getId(), e.getUserId(), e.getInstitution(), e.getDegree(),
                e.getFieldOfStudy(), e.getStartDate(), e.getEndDate(), e.getDescription(),
                e.getGrade(), e.getDisplayOrder(), e.getCreatedAt(), e.getUpdatedAt(),
                skills != null ? skills : List.of());
    }

    public static EducationEntity toEntity(Education d) {
        EducationEntity e = new EducationEntity();
        e.setId(d.id());
        e.setUserId(d.userId());
        e.setInstitution(d.institution());
        e.setDegree(d.degree());
        e.setFieldOfStudy(d.fieldOfStudy());
        e.setStartDate(d.startDate());
        e.setEndDate(d.endDate());
        e.setDescription(d.description());
        e.setGrade(d.grade());
        e.setDisplayOrder(d.displayOrder());
        return e;
    }

    // ── Certification ─────────────────────────────────────────────────────────

    public static Certification toDomain(CertificationEntity e) {
        return new Certification(e.getId(), e.getUserId(), e.getName(), e.getIssuer(),
                e.getIssuedAt(), e.getExpiresAt(), e.getCredentialUrl(), e.getCreatedAt());
    }

    public static CertificationEntity toEntity(Certification d) {
        CertificationEntity e = new CertificationEntity();
        e.setId(d.id());
        e.setUserId(d.userId());
        e.setName(d.name());
        e.setIssuer(d.issuer());
        e.setIssuedAt(d.issuedAt());
        e.setExpiresAt(d.expiresAt());
        e.setCredentialUrl(d.credentialUrl());
        return e;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static List<String> toList(String[] arr) {
        return arr != null ? Arrays.asList(arr) : List.of();
    }

    private static String[] toArray(List<String> l) {
        return l != null ? l.toArray(String[]::new) : new String[0];
    }
}
