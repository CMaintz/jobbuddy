package com.autoapplicant.adapter.persistence.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "education_skills")
@IdClass(EducationSkillId.class)
public class EducationSkillEntity {

    @Id
    @Column(name = "education_id")
    private UUID educationId;

    @Id
    @Column(name = "taxonomy_id")
    private UUID taxonomyId;

    public UUID getEducationId() { return educationId; }
    public void setEducationId(UUID v) { this.educationId = v; }
    public UUID getTaxonomyId() { return taxonomyId; }
    public void setTaxonomyId(UUID v) { this.taxonomyId = v; }
}
