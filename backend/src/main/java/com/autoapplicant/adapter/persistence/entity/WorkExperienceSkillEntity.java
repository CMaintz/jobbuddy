package com.autoapplicant.adapter.persistence.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "work_experience_skills")
@IdClass(WorkExperienceSkillId.class)
public class WorkExperienceSkillEntity {

    @Id
    @Column(name = "work_experience_id")
    private UUID workExperienceId;

    @Id
    @Column(name = "taxonomy_id")
    private UUID taxonomyId;

    public UUID getWorkExperienceId() { return workExperienceId; }
    public void setWorkExperienceId(UUID v) { this.workExperienceId = v; }
    public UUID getTaxonomyId() { return taxonomyId; }
    public void setTaxonomyId(UUID v) { this.taxonomyId = v; }
}
