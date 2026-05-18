package com.autoapplicant.adapter.persistence.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class WorkExperienceSkillId implements Serializable {

    private UUID workExperienceId;
    private UUID taxonomyId;

    public WorkExperienceSkillId() {}

    public WorkExperienceSkillId(UUID workExperienceId, UUID taxonomyId) {
        this.workExperienceId = workExperienceId;
        this.taxonomyId = taxonomyId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WorkExperienceSkillId that)) return false;
        return Objects.equals(workExperienceId, that.workExperienceId) &&
               Objects.equals(taxonomyId, that.taxonomyId);
    }

    @Override
    public int hashCode() { return Objects.hash(workExperienceId, taxonomyId); }
}
