package com.autoapplicant.adapter.persistence.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "project_skills")
@IdClass(ProjectSkillId.class)
public class ProjectSkillEntity {

    @Id
    @Column(name = "project_id")
    private UUID projectId;

    @Id
    @Column(name = "taxonomy_id")
    private UUID taxonomyId;

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID v) { this.projectId = v; }
    public UUID getTaxonomyId() { return taxonomyId; }
    public void setTaxonomyId(UUID v) { this.taxonomyId = v; }
}
