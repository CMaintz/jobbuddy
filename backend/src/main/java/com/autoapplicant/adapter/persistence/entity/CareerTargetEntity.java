package com.autoapplicant.adapter.persistence.entity;

import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "career_target")
public class CareerTargetEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Type(StringArrayType.class)
    @Column(name = "target_archetypes", columnDefinition = "text[]")
    private String[] targetArchetypes;

    @Column(name = "north_star", columnDefinition = "text")
    private String northStar;

    @Column(name = "narrative", columnDefinition = "text")
    private String narrative;

    @Type(StringArrayType.class)
    @Column(name = "culture_requirements", columnDefinition = "text[]")
    private String[] cultureRequirements;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist @PreUpdate
    void touch() { updatedAt = Instant.now(); }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String[] getTargetArchetypes() { return targetArchetypes; }
    public void setTargetArchetypes(String[] targetArchetypes) { this.targetArchetypes = targetArchetypes; }
    public String getNorthStar() { return northStar; }
    public void setNorthStar(String northStar) { this.northStar = northStar; }
    public String getNarrative() { return narrative; }
    public void setNarrative(String narrative) { this.narrative = narrative; }
    public String[] getCultureRequirements() { return cultureRequirements; }
    public void setCultureRequirements(String[] cultureRequirements) { this.cultureRequirements = cultureRequirements; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
