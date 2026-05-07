package com.autoapplicant.adapter.persistence.entity;

import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "writing_profiles")
public class WritingProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(columnDefinition = "text")
    private String tone;

    @Column(name = "vocabulary_notes", columnDefinition = "text")
    private String vocabularyNotes;

    @Type(StringArrayType.class)
    @Column(name = "phrasing_patterns", columnDefinition = "text[]")
    private String[] phrasingPatterns;

    @Type(StringArrayType.class)
    @Column(name = "example_excerpts", columnDefinition = "text[]")
    private String[] exampleExcerpts;

    @Column(name = "last_analyzed_at")
    private Instant lastAnalyzedAt;

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
    public String getTone() { return tone; }
    public void setTone(String tone) { this.tone = tone; }
    public String getVocabularyNotes() { return vocabularyNotes; }
    public void setVocabularyNotes(String vocabularyNotes) { this.vocabularyNotes = vocabularyNotes; }
    public String[] getPhrasing_patterns() { return phrasingPatterns; }
    public void setPhrasing_patterns(String[] phrasingPatterns) { this.phrasingPatterns = phrasingPatterns; }
    public String[] getExampleExcerpts() { return exampleExcerpts; }
    public void setExampleExcerpts(String[] exampleExcerpts) { this.exampleExcerpts = exampleExcerpts; }
    public Instant getLastAnalyzedAt() { return lastAnalyzedAt; }
    public void setLastAnalyzedAt(Instant lastAnalyzedAt) { this.lastAnalyzedAt = lastAnalyzedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
