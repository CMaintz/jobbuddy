package com.autoapplicant.adapter.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "generated_documents")
public class GeneratedDocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "document_type", nullable = false)
    private String documentType;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "prompt_template_id")
    private UUID promptTemplateId;

    @Column(name = "cv_version_id")
    private UUID cvVersionId;

    @Column(name = "model_used")
    private String modelUsed;

    @Column(name = "tokens_used")
    private Integer tokensUsed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist void prePersist() { createdAt = Instant.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getApplicationId() { return applicationId; }
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }
    public UUID getJobId() { return jobId; }
    public void setJobId(UUID jobId) { this.jobId = jobId; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public UUID getPromptTemplateId() { return promptTemplateId; }
    public void setPromptTemplateId(UUID promptTemplateId) { this.promptTemplateId = promptTemplateId; }
    public UUID getCvVersionId() { return cvVersionId; }
    public void setCvVersionId(UUID cvVersionId) { this.cvVersionId = cvVersionId; }
    public String getModelUsed() { return modelUsed; }
    public void setModelUsed(String modelUsed) { this.modelUsed = modelUsed; }
    public Integer getTokensUsed() { return tokensUsed; }
    public void setTokensUsed(Integer tokensUsed) { this.tokensUsed = tokensUsed; }
    public Instant getCreatedAt() { return createdAt; }
}
