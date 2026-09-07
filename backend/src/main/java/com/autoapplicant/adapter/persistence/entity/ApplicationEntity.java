package com.autoapplicant.adapter.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "applications")
public class ApplicationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(nullable = false)
    private String status;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(name = "recruiter_name")
    private String recruiterName;

    @Column(name = "recruiter_email")
    private String recruiterEmail;

    @Column(name = "cover_letter_text", columnDefinition = "text")
    private String coverLetterText;

    @Column(name = "application_text", columnDefinition = "text")
    private String applicationText;

    @Column(name = "recruiter_message", columnDefinition = "text")
    private String recruiterMessage;

    @Column(name = "recruiter_reply", columnDefinition = "text")
    private String recruiterReply;

    @Column(name = "cv_version_id")
    private UUID cvVersionId;

    @Column(name = "prompt_template_id")
    private UUID promptTemplateId;

    @Column(name = "match_score")
    private Integer matchScore;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist void prePersist() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getJobId() { return jobId; }
    public void setJobId(UUID jobId) { this.jobId = jobId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getAppliedAt() { return appliedAt; }
    public void setAppliedAt(Instant appliedAt) { this.appliedAt = appliedAt; }
    public String getRecruiterName() { return recruiterName; }
    public void setRecruiterName(String recruiterName) { this.recruiterName = recruiterName; }
    public String getRecruiterEmail() { return recruiterEmail; }
    public void setRecruiterEmail(String recruiterEmail) { this.recruiterEmail = recruiterEmail; }
    public String getCoverLetterText() { return coverLetterText; }
    public void setCoverLetterText(String coverLetterText) { this.coverLetterText = coverLetterText; }
    public String getApplicationText() { return applicationText; }
    public void setApplicationText(String applicationText) { this.applicationText = applicationText; }
    public String getRecruiterMessage() { return recruiterMessage; }
    public void setRecruiterMessage(String recruiterMessage) { this.recruiterMessage = recruiterMessage; }
    public String getRecruiterReply() { return recruiterReply; }
    public void setRecruiterReply(String recruiterReply) { this.recruiterReply = recruiterReply; }
    public UUID getCvVersionId() { return cvVersionId; }
    public void setCvVersionId(UUID cvVersionId) { this.cvVersionId = cvVersionId; }
    public UUID getPromptTemplateId() { return promptTemplateId; }
    public void setPromptTemplateId(UUID promptTemplateId) { this.promptTemplateId = promptTemplateId; }
    public Integer getMatchScore() { return matchScore; }
    public void setMatchScore(Integer matchScore) { this.matchScore = matchScore; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
