package com.autoapplicant.adapter.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "profile_skills")
public class ProfileSkillEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "skill_name", nullable = false)
    private String skillName;

    @Column(name = "taxonomy_id")
    private UUID taxonomyId;

    @Column(name = "proficiency_level")
    private String proficiencyLevel = "INTERMEDIATE";

    @Column(name = "years_experience")
    private Integer yearsExperience;

    @Column(name = "used_in_production")
    private boolean usedInProduction;

    @Column(name = "display_order")
    private int displayOrder;

    // Stored rather than always derived: a skill outside the taxonomy has no row to derive
    // from, and until V070 those stayed uncategorised permanently.
    @Column(name = "category")
    private String category;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist void prePersist() { createdAt = Instant.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getSkillName() { return skillName; }
    public void setSkillName(String skillName) { this.skillName = skillName; }
    public UUID getTaxonomyId() { return taxonomyId; }
    public void setTaxonomyId(UUID taxonomyId) { this.taxonomyId = taxonomyId; }
    public String getProficiencyLevel() { return proficiencyLevel; }
    public void setProficiencyLevel(String proficiencyLevel) { this.proficiencyLevel = proficiencyLevel; }
    public Integer getYearsExperience() { return yearsExperience; }
    public void setYearsExperience(Integer yearsExperience) { this.yearsExperience = yearsExperience; }
    public boolean isUsedInProduction() { return usedInProduction; }
    public void setUsedInProduction(boolean usedInProduction) { this.usedInProduction = usedInProduction; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Instant getCreatedAt() { return createdAt; }
}
