package com.autoapplicant.adapter.persistence.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class ProjectSkillId implements Serializable {

    private UUID projectId;
    private UUID taxonomyId;

    public ProjectSkillId() {}

    public ProjectSkillId(UUID projectId, UUID taxonomyId) {
        this.projectId = projectId;
        this.taxonomyId = taxonomyId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProjectSkillId that)) return false;
        return Objects.equals(projectId, that.projectId) &&
               Objects.equals(taxonomyId, that.taxonomyId);
    }

    @Override
    public int hashCode() { return Objects.hash(projectId, taxonomyId); }
}
