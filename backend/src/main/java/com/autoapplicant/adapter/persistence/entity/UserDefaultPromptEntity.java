package com.autoapplicant.adapter.persistence.entity;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Which prompt template a user wants for a category — their override of the app's default. */
@Entity
@Table(name = "user_default_prompt")
@IdClass(UserDefaultPromptEntity.Key.class)
public class UserDefaultPromptEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist @PreUpdate
    void touch() { updatedAt = Instant.now(); }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public UUID getTemplateId() { return templateId; }
    public void setTemplateId(UUID templateId) { this.templateId = templateId; }
    public Instant getUpdatedAt() { return updatedAt; }

    public static class Key implements Serializable {
        private UUID userId;
        private String category;

        public Key() {}
        public Key(UUID userId, String category) { this.userId = userId; this.category = category; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return Objects.equals(userId, key.userId) && Objects.equals(category, key.category);
        }
        @Override public int hashCode() { return Objects.hash(userId, category); }
    }
}
