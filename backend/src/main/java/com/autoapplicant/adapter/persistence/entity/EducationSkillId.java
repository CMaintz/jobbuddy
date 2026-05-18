package com.autoapplicant.adapter.persistence.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class EducationSkillId implements Serializable {

    private UUID educationId;
    private UUID taxonomyId;

    public EducationSkillId() {}

    public EducationSkillId(UUID educationId, UUID taxonomyId) {
        this.educationId = educationId;
        this.taxonomyId = taxonomyId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EducationSkillId that)) return false;
        return Objects.equals(educationId, that.educationId) &&
               Objects.equals(taxonomyId, that.taxonomyId);
    }

    @Override
    public int hashCode() { return Objects.hash(educationId, taxonomyId); }
}
