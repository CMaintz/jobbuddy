package com.autoapplicant.adapter.persistence.mapper;

import com.autoapplicant.adapter.persistence.entity.ApplicationEntity;
import com.autoapplicant.domain.application.Application;
import com.autoapplicant.domain.application.ApplicationStatus;

public final class ApplicationMapper {

    private ApplicationMapper() {}

    public static Application toDomain(ApplicationEntity e) {
        return new Application(
                e.getId(), e.getUserId(), e.getJobId(),
                ApplicationStatus.valueOf(e.getStatus()),
                e.getAppliedAt(), e.getRecruiterName(), e.getRecruiterEmail(),
                e.getCoverLetterText(), e.getApplicationText(), e.getRecruiterMessage(),
                e.getCvVersionId(), e.getPromptTemplateId(), e.getMatchScore(),
                e.getNotes(), e.getCreatedAt(), e.getUpdatedAt()
        );
    }

    public static ApplicationEntity toEntity(Application d) {
        ApplicationEntity e = new ApplicationEntity();
        e.setId(d.id());
        e.setUserId(d.userId());
        e.setJobId(d.jobId());
        e.setStatus(d.status().name());
        e.setAppliedAt(d.appliedAt());
        e.setRecruiterName(d.recruiterName());
        e.setRecruiterEmail(d.recruiterEmail());
        e.setCoverLetterText(d.coverLetterText());
        e.setApplicationText(d.applicationText());
        e.setRecruiterMessage(d.recruiterMessage());
        e.setCvVersionId(d.cvVersionId());
        e.setPromptTemplateId(d.promptTemplateId());
        e.setMatchScore(d.matchScore());
        e.setNotes(d.notes());
        return e;
    }
}
