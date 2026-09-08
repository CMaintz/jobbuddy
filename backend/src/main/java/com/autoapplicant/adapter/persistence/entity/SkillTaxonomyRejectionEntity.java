package com.autoapplicant.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "skill_taxonomy_rejections")
public class SkillTaxonomyRejectionEntity {

    /** The normalized name is the identity — a label is rejected, not a row. */
    @Id
    @Column(name = "normalized_name", nullable = false, length = 200)
    private String normalizedName;

    @Column(name = "label", nullable = false, length = 200)
    private String label;

    @Column(name = "reason")
    private String reason;

    @Column(name = "rejected_at", nullable = false)
    private Instant rejectedAt = Instant.now();

    public String getNormalizedName() { return normalizedName; }
    public void setNormalizedName(String normalizedName) { this.normalizedName = normalizedName; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Instant getRejectedAt() { return rejectedAt; }
    public void setRejectedAt(Instant rejectedAt) { this.rejectedAt = rejectedAt; }
}
