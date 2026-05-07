package com.autoapplicant.adapter.persistence.entity;

import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "profiles")
public class ProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "full_name")
    private String fullName;

    private String headline;
    private String summary;
    private String location;
    private String municipality;

    @Column(name = "linkedin_url")
    private String linkedinUrl;

    @Column(name = "github_url")
    private String githubUrl;

    @Column(name = "website_url")
    private String websiteUrl;

    @Column(name = "years_experience")
    private Integer yearsExperience;

    @Type(StringArrayType.class)
    @Column(columnDefinition = "text[]")
    private String[] skills;

    @Type(StringArrayType.class)
    @Column(columnDefinition = "text[]")
    private String[] technologies;

    @Type(StringArrayType.class)
    @Column(columnDefinition = "text[]")
    private String[] languages;

    @Column(name = "desired_salary_min")
    private Integer desiredSalaryMin;

    @Column(name = "desired_salary_max")
    private Integer desiredSalaryMax;

    @Column(name = "desired_currency")
    private String desiredCurrency;

    @Column(name = "remote_preference")
    private String remotePreference;

    @Column(name = "employment_type_preference")
    private String employmentTypePreference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist void prePersist() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getMunicipality() { return municipality; }
    public void setMunicipality(String municipality) { this.municipality = municipality; }
    public String getLinkedinUrl() { return linkedinUrl; }
    public void setLinkedinUrl(String linkedinUrl) { this.linkedinUrl = linkedinUrl; }
    public String getGithubUrl() { return githubUrl; }
    public void setGithubUrl(String githubUrl) { this.githubUrl = githubUrl; }
    public String getWebsiteUrl() { return websiteUrl; }
    public void setWebsiteUrl(String websiteUrl) { this.websiteUrl = websiteUrl; }
    public Integer getYearsExperience() { return yearsExperience; }
    public void setYearsExperience(Integer yearsExperience) { this.yearsExperience = yearsExperience; }
    public String[] getSkills() { return skills; }
    public void setSkills(String[] skills) { this.skills = skills; }
    public String[] getTechnologies() { return technologies; }
    public void setTechnologies(String[] technologies) { this.technologies = technologies; }
    public String[] getLanguages() { return languages; }
    public void setLanguages(String[] languages) { this.languages = languages; }
    public Integer getDesiredSalaryMin() { return desiredSalaryMin; }
    public void setDesiredSalaryMin(Integer desiredSalaryMin) { this.desiredSalaryMin = desiredSalaryMin; }
    public Integer getDesiredSalaryMax() { return desiredSalaryMax; }
    public void setDesiredSalaryMax(Integer desiredSalaryMax) { this.desiredSalaryMax = desiredSalaryMax; }
    public String getDesiredCurrency() { return desiredCurrency; }
    public void setDesiredCurrency(String desiredCurrency) { this.desiredCurrency = desiredCurrency; }
    public String getRemotePreference() { return remotePreference; }
    public void setRemotePreference(String remotePreference) { this.remotePreference = remotePreference; }
    public String getEmploymentTypePreference() { return employmentTypePreference; }
    public void setEmploymentTypePreference(String employmentTypePreference) { this.employmentTypePreference = employmentTypePreference; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
