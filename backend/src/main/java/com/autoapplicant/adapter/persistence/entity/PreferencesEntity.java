package com.autoapplicant.adapter.persistence.entity;

import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "preferences")
public class PreferencesEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Type(StringArrayType.class)
    @Column(name = "preferred_locations", columnDefinition = "text[]")
    private String[] preferredLocations;

    @Type(StringArrayType.class)
    @Column(name = "preferred_municipalities", columnDefinition = "text[]")
    private String[] preferredMunicipalities;

    @Type(StringArrayType.class)
    @Column(name = "positive_signals", columnDefinition = "text[]")
    private String[] positiveSignals;

    @Type(StringArrayType.class)
    @Column(name = "negative_signals", columnDefinition = "text[]")
    private String[] negativeSignals;

    @Type(StringArrayType.class)
    @Column(name = "excluded_companies", columnDefinition = "text[]")
    private String[] excludedCompanies;

    @Type(StringArrayType.class)
    @Column(name = "preferred_remote_types", columnDefinition = "text[]")
    private String[] preferredRemoteTypes;

    @Type(StringArrayType.class)
    @Column(name = "preferred_employment_types", columnDefinition = "text[]")
    private String[] preferredEmploymentTypes;

    @Type(StringArrayType.class)
    @Column(name = "preferred_seniority", columnDefinition = "text[]")
    private String[] preferredSeniority;

    @Type(StringArrayType.class)
    @Column(name = "preferred_industries", columnDefinition = "text[]")
    private String[] preferredIndustries;

    @Column(name = "salary_min")
    private Integer salaryMin;

    @Column(name = "salary_max")
    private Integer salaryMax;

    @Column(name = "max_commute_km")
    private Integer maxCommuteKm;

    @Column(name = "notification_enabled")
    private boolean notificationEnabled;

    @Column(name = "notification_frequency")
    private String notificationFrequency;

    @Column(name = "weekly_application_goal")
    private Integer weeklyApplicationGoal;

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
    public String[] getPreferredLocations() { return preferredLocations; }
    public void setPreferredLocations(String[] v) { this.preferredLocations = v; }
    public String[] getPreferredMunicipalities() { return preferredMunicipalities; }
    public void setPreferredMunicipalities(String[] v) { this.preferredMunicipalities = v; }
    public String[] getPositiveSignals() { return positiveSignals; }
    public void setPositiveSignals(String[] v) { this.positiveSignals = v; }
    public String[] getNegativeSignals() { return negativeSignals; }
    public void setNegativeSignals(String[] v) { this.negativeSignals = v; }
    public String[] getExcludedCompanies() { return excludedCompanies; }
    public void setExcludedCompanies(String[] v) { this.excludedCompanies = v; }
    public String[] getPreferredRemoteTypes() { return preferredRemoteTypes; }
    public void setPreferredRemoteTypes(String[] v) { this.preferredRemoteTypes = v; }
    public String[] getPreferredEmploymentTypes() { return preferredEmploymentTypes; }
    public void setPreferredEmploymentTypes(String[] v) { this.preferredEmploymentTypes = v; }
    public String[] getPreferredSeniority() { return preferredSeniority; }
    public void setPreferredSeniority(String[] v) { this.preferredSeniority = v; }
    public String[] getPreferredIndustries() { return preferredIndustries; }
    public void setPreferredIndustries(String[] v) { this.preferredIndustries = v; }
    public Integer getSalaryMin() { return salaryMin; }
    public void setSalaryMin(Integer v) { this.salaryMin = v; }
    public Integer getSalaryMax() { return salaryMax; }
    public void setSalaryMax(Integer v) { this.salaryMax = v; }
    public Integer getMaxCommuteKm() { return maxCommuteKm; }
    public void setMaxCommuteKm(Integer v) { this.maxCommuteKm = v; }
    public boolean isNotificationEnabled() { return notificationEnabled; }
    public void setNotificationEnabled(boolean v) { this.notificationEnabled = v; }
    public String getNotificationFrequency() { return notificationFrequency; }
    public void setNotificationFrequency(String v) { this.notificationFrequency = v; }
    public Integer getWeeklyApplicationGoal() { return weeklyApplicationGoal; }
    public void setWeeklyApplicationGoal(Integer v) { this.weeklyApplicationGoal = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
