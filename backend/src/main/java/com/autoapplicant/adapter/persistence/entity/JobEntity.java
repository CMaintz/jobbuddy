package com.autoapplicant.adapter.persistence.entity;

import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "jobs")
public class JobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String source;

    @Column(name = "source_job_id")
    private String sourceJobId;

    @Column(nullable = false, length = 2000)
    private String url;

    @Column(nullable = false)
    private String title;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "description_raw", columnDefinition = "text")
    private String descriptionRaw;

    @Column(name = "description_clean", columnDefinition = "text")
    private String descriptionClean;

    @Column(name = "employment_type")
    private String employmentType;

    private String seniority;

    @Column(name = "remote_type")
    private String remoteType;

    private String location;
    private String municipality;
    private String region;
    private String country;

    @Column(name = "salary_min")
    private Integer salaryMin;

    @Column(name = "salary_max")
    private Integer salaryMax;

    private String currency;

    // String[] + StringArrayType maps to PostgreSQL text[] natively (no join table needed).
    @Type(StringArrayType.class)
    @Column(columnDefinition = "text[]")
    private String[] technologies;

    @Type(StringArrayType.class)
    @Column(columnDefinition = "text[]")
    private String[] skills;

    @Type(StringArrayType.class)
    @Column(columnDefinition = "text[]")
    private String[] languages;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "scraped_at", nullable = false)
    private Instant scrapedAt;

    @Column(name = "ai_summary", columnDefinition = "text")
    private String aiSummary;

    @Type(StringArrayType.class)
    @Column(name = "ai_tags", columnDefinition = "text[]") // text[] — see comment above
    private String[] aiTags;

    @Column(name = "ai_seniority_estimate")
    private String aiSeniorityEstimate;

    @Column(name = "duplicate_group_id")
    private UUID duplicateGroupId;

    @Column(name = "is_active")
    private boolean isActive = true;

    @Column(name = "job_category")
    private String jobCategory;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "short_description", columnDefinition = "text")
    private String shortDescription;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @PrePersist void prePersist() {
        if (scrapedAt == null) scrapedAt = Instant.now();
        createdAt = updatedAt = Instant.now();
    }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getSourceJobId() { return sourceJobId; }
    public void setSourceJobId(String sourceJobId) { this.sourceJobId = sourceJobId; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public UUID getCompanyId() { return companyId; }
    public void setCompanyId(UUID companyId) { this.companyId = companyId; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getDescriptionRaw() { return descriptionRaw; }
    public void setDescriptionRaw(String descriptionRaw) { this.descriptionRaw = descriptionRaw; }
    public String getDescriptionClean() { return descriptionClean; }
    public void setDescriptionClean(String descriptionClean) { this.descriptionClean = descriptionClean; }
    public String getEmploymentType() { return employmentType; }
    public void setEmploymentType(String employmentType) { this.employmentType = employmentType; }
    public String getSeniority() { return seniority; }
    public void setSeniority(String seniority) { this.seniority = seniority; }
    public String getRemoteType() { return remoteType; }
    public void setRemoteType(String remoteType) { this.remoteType = remoteType; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getMunicipality() { return municipality; }
    public void setMunicipality(String municipality) { this.municipality = municipality; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public Integer getSalaryMin() { return salaryMin; }
    public void setSalaryMin(Integer salaryMin) { this.salaryMin = salaryMin; }
    public Integer getSalaryMax() { return salaryMax; }
    public void setSalaryMax(Integer salaryMax) { this.salaryMax = salaryMax; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String[] getTechnologies() { return technologies; }
    public void setTechnologies(String[] technologies) { this.technologies = technologies; }
    public String[] getSkills() { return skills; }
    public void setSkills(String[] skills) { this.skills = skills; }
    public String[] getLanguages() { return languages; }
    public void setLanguages(String[] languages) { this.languages = languages; }
    public Instant getPostedAt() { return postedAt; }
    public void setPostedAt(Instant postedAt) { this.postedAt = postedAt; }
    public Instant getScrapedAt() { return scrapedAt; }
    public void setScrapedAt(Instant scrapedAt) { this.scrapedAt = scrapedAt; }
    public String getAiSummary() { return aiSummary; }
    public void setAiSummary(String aiSummary) { this.aiSummary = aiSummary; }
    public String[] getAiTags() { return aiTags; }
    public void setAiTags(String[] aiTags) { this.aiTags = aiTags; }
    public String getAiSeniorityEstimate() { return aiSeniorityEstimate; }
    public void setAiSeniorityEstimate(String aiSeniorityEstimate) { this.aiSeniorityEstimate = aiSeniorityEstimate; }
    public UUID getDuplicateGroupId() { return duplicateGroupId; }
    public void setDuplicateGroupId(UUID duplicateGroupId) { this.duplicateGroupId = duplicateGroupId; }
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    public String getJobCategory() { return jobCategory; }
    public void setJobCategory(String jobCategory) { this.jobCategory = jobCategory; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getShortDescription() { return shortDescription; }
    public void setShortDescription(String shortDescription) { this.shortDescription = shortDescription; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
}
