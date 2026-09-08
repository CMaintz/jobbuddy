package com.autoapplicant.adapter.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_quality_score")
public class DocumentQualityScoreEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "generated_document_id")
    private UUID generatedDocumentId;

    @Column(name = "document_type", nullable = false)
    private String documentType;

    @Column(nullable = false)
    private int total;

    @Column(nullable = false, columnDefinition = "text")
    private String dimensions;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getGeneratedDocumentId() { return generatedDocumentId; }
    public void setGeneratedDocumentId(UUID generatedDocumentId) { this.generatedDocumentId = generatedDocumentId; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
    public String getDimensions() { return dimensions; }
    public void setDimensions(String dimensions) { this.dimensions = dimensions; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
